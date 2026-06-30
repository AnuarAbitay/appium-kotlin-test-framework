package io.github.january.appium.runtime

import io.github.january.appium.config.FrameworkConfig
import io.github.january.appium.config.FrameworkConfigLoader
import io.github.january.appium.device.config.DeviceConfigLoader
import io.github.january.appium.device.config.DeviceConfigValidator
import io.github.january.appium.device.data.Device
import io.github.january.appium.device.data.DeviceConfig
import io.github.january.appium.device.platforms.AndroidEmulatorManager
import io.github.january.appium.device.platforms.IOSSimulatorManager
import io.github.january.appium.device.pool.DevicePool
import io.github.january.appium.driver.DriverManager
import io.github.january.appium.driver.factory.AndroidDriverFactory
import io.github.january.appium.driver.factory.IOSDriverFactory
import io.github.january.appium.driver.options.AndroidOptionsFactory
import io.github.january.appium.driver.options.IOSOptionsFactory
import io.github.january.appium.server.AppiumServerFactory
import io.github.january.appium.server.AppiumServerManager

class FrameworkRuntime private constructor(
    val config: FrameworkConfig,
    val devices: List<Device>,
    val devicePool: DevicePool,
    val serverManager: AppiumServerManager,
    val driverManager: DriverManager
) {

    private val lifecycleLock = Any()

    private var state = State.CREATED

    fun start() {
        synchronized(lifecycleLock) {
            check(state == State.CREATED) {
                "FrameworkRuntime cannot be started from state $state"
            }

            try {
                bootDevices()

                serverManager.startServersForDevices(devices)

                state = State.STARTED
            } catch (startException: Exception) {
                cleanupAfterFailedStart(startException)

                state = State.STOPPED

                throw IllegalStateException(
                    "Failed to start mobile framework runtime",
                    startException
                )
            }
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            if (state == State.STOPPED) {
                return
            }

            val failures = mutableListOf<Throwable>()

            runCleanupStep(
                failures = failures,
                stepName = "quit mobile drivers"
            ) {
                driverManager.quitAllDrivers()
            }

            runCleanupStep(
                failures = failures,
                stepName = "stop Appium servers"
            ) {
                serverManager.stopAllServers()
            }

            runCleanupStep(
                failures = failures,
                stepName = "shutdown mobile devices"
            ) {
                shutdownDevices()
            }

            runCleanupStep(
                failures = failures,
                stepName = "clear DevicePool"
            ) {
                devicePool.clear()
            }

            state = State.STOPPED

            if (failures.isNotEmpty()) {
                val exception = IllegalStateException(
                    "FrameworkRuntime stopped with " +
                            "${failures.size} cleanup failure(s)"
                )

                failures.forEach(exception::addSuppressed)

                throw exception
            }
        }
    }

    fun isStarted(): Boolean {
        return synchronized(lifecycleLock) {
            state == State.STARTED
        }
    }

    private fun bootDevices() {
        when {
            devices.all(Device::isAndroid) -> {
                AndroidEmulatorManager.bootEmulators(devices)
            }

            devices.all(Device::isIos) -> {
                IOSSimulatorManager.bootSimulators(devices)
            }

            else -> error(
                "Devices from different platforms cannot be started " +
                        "in the same runtime"
            )
        }
    }

    private fun shutdownDevices() {
        when {
            devices.all(Device::isAndroid) -> {
                AndroidEmulatorManager.shutdownEmulators(devices)
            }

            devices.all(Device::isIos) -> {
                IOSSimulatorManager.shutdownSimulators(devices)
            }

            else -> error(
                "Devices from different platforms cannot be stopped " +
                        "in the same runtime"
            )
        }
    }

    private fun cleanupAfterFailedStart(
        startException: Exception
    ) {
        runCatching {
            driverManager.quitAllDrivers()
        }.exceptionOrNull()?.let(startException::addSuppressed)

        runCatching {
            serverManager.stopAllServers()
        }.exceptionOrNull()?.let(startException::addSuppressed)

        runCatching {
            shutdownDevices()
        }.exceptionOrNull()?.let(startException::addSuppressed)

        runCatching {
            devicePool.clear()
        }.exceptionOrNull()?.let(startException::addSuppressed)
    }

    private fun runCleanupStep(
        failures: MutableList<Throwable>,
        stepName: String,
        action: () -> Unit
    ) {
        try {
            action()
        } catch (exception: Exception) {
            failures += IllegalStateException(
                "Failed to $stepName",
                exception
            )
        }
    }

    private enum class State {
        CREATED,
        STARTED,
        STOPPED
    }

    companion object {

        fun create(
            deviceIds: List<String>,
            config: FrameworkConfig = FrameworkConfigLoader.load(),
            deviceConfig: DeviceConfig = DeviceConfigLoader.load()
        ): FrameworkRuntime {
            val selectedDevices = selectDevices(
                availableDevices = deviceConfig.devices,
                requestedIds = deviceIds
            )

            validateSinglePlatform(selectedDevices)

            DeviceConfigValidator.validate(
                DeviceConfig(devices = selectedDevices)
            )

            val devicePool = DevicePool().apply {
                initialize(selectedDevices)
            }

            val serverFactory = AppiumServerFactory(
                appiumJsPath = config.appiumJsPath
            )

            val serverManager = AppiumServerManager(
                serverFactory = serverFactory
            )

            val driverManager = createDriverManager(
                config = config,
                devices = selectedDevices,
                serverManager = serverManager
            )

            return FrameworkRuntime(
                config = config,
                devices = selectedDevices,
                devicePool = devicePool,
                serverManager = serverManager,
                driverManager = driverManager
            )
        }

        private fun selectDevices(
            availableDevices: List<Device>,
            requestedIds: List<String>
        ): List<Device> {
            require(requestedIds.isNotEmpty()) {
                "At least one device id must be provided"
            }

            val normalizedIds = requestedIds.map(String::trim)

            require(normalizedIds.none(String::isBlank)) {
                "Device ids must not be blank"
            }

            val duplicateRequestedIds = normalizedIds
                .groupingBy { id -> id }
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys

            require(duplicateRequestedIds.isEmpty()) {
                "Duplicate requested device ids: " +
                        duplicateRequestedIds.joinToString()
            }

            val devicesById = availableDevices.groupBy(Device::id)

            return normalizedIds.map { requestedId ->
                val matchingDevices = devicesById[requestedId]
                    ?: error(
                        "Device '$requestedId' was not found in devices.yml"
                    )

                require(matchingDevices.size == 1) {
                    "Device id '$requestedId' is configured " +
                            "${matchingDevices.size} times in devices.yml"
                }

                matchingDevices.single()
            }
        }

        private fun validateSinglePlatform(
            devices: List<Device>
        ) {
            val isAndroidRun = devices.all(Device::isAndroid)
            val isIosRun = devices.all(Device::isIos)

            require(isAndroidRun || isIosRun) {
                val configuredPlatforms = devices.joinToString { device ->
                    "${device.id}=${device.platformName}"
                }

                "Devices from different platforms cannot be used " +
                        "in the same run: $configuredPlatforms"
            }
        }

        private fun createDriverManager(
            config: FrameworkConfig,
            devices: List<Device>,
            serverManager: AppiumServerManager
        ): DriverManager {
            return when {
                devices.all(Device::isAndroid) -> {
                    createAndroidDriverManager(
                        config = config,
                        serverManager = serverManager
                    )
                }

                devices.all(Device::isIos) -> {
                    createIosDriverManager(
                        config = config,
                        devices = devices,
                        serverManager = serverManager
                    )
                }

                else -> error(
                    "Unable to determine platform for selected devices"
                )
            }
        }

        private fun createAndroidDriverManager(
            config: FrameworkConfig,
            serverManager: AppiumServerManager
        ): DriverManager {
            val optionsFactory = AndroidOptionsFactory(
                appPath = config.requireAndroidAppPath(),
                language = config.language,
                locale = config.locale
            )

            val driverFactory = AndroidDriverFactory(
                optionsFactory = optionsFactory,
                serverManager = serverManager
            )

            return DriverManager.forAndroid(driverFactory)
        }

        private fun createIosDriverManager(
            config: FrameworkConfig,
            devices: List<Device>,
            serverManager: AppiumServerManager
        ): DriverManager {
            val hasSimulators = devices.any(Device::isEmulator)
            val hasRealDevices = devices.any { !it.isEmulator }

            val simulatorAppPath = if (hasSimulators) {
                config.requireIosSimulatorAppPath()
            } else {
                config.iosSimulatorAppPath.orEmpty()
            }

            val realDeviceAppPath = if (hasRealDevices) {
                config.requireIosRealDeviceAppPath()
            } else {
                config.iosRealDeviceAppPath.orEmpty()
            }

            val optionsFactory = IOSOptionsFactory(
                simulatorAppPath = simulatorAppPath,
                realDeviceAppPath = realDeviceAppPath,
                language = config.language,
                locale = config.locale
            )

            val driverFactory = IOSDriverFactory(
                optionsFactory = optionsFactory,
                serverManager = serverManager
            )

            return DriverManager.forIos(driverFactory)
        }
    }
}