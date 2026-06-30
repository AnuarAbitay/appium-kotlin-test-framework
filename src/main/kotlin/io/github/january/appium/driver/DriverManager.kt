package io.github.january.appium.driver

import io.appium.java_client.AppiumDriver
import io.github.january.appium.device.data.Device
import io.github.january.appium.driver.factory.AndroidDriverFactory
import io.github.january.appium.driver.factory.IOSDriverFactory
import java.util.concurrent.ConcurrentHashMap

class DriverManager private constructor(
    private val platformName: String,
    private val supportsDevice: (Device) -> Boolean,
    private val driverFactory: (Device) -> AppiumDriver
) {

    private val drivers =
        ConcurrentHashMap<String, AppiumDriver>()

    fun createDriverForDevice(
        device: Device
    ): AppiumDriver {
        require(supportsDevice(device)) {
            "DriverManager is configured for $platformName, " +
                    "but device '${device.id}' uses platform " +
                    "'${device.platformName}'"
        }

        drivers[device.id]?.let { existingDriver ->
            return existingDriver
        }

        val createdDriver = driverFactory(device)

        val existingDriver = drivers.putIfAbsent(
            device.id,
            createdDriver
        )

        if (existingDriver != null) {
            runCatching {
                createdDriver.quit()
            }

            return existingDriver
        }

        return createdDriver
    }

    fun getDriverForDevice(
        device: Device
    ): AppiumDriver {
        return drivers[device.id]
            ?: error(
                "Driver for device '${device.id}' " +
                        "has not been created"
            )
    }

    fun hasDriverForDevice(
        device: Device
    ): Boolean {
        return drivers.containsKey(device.id)
    }

    fun quitDriverForDevice(
        device: Device
    ) {
        val driver = drivers.remove(device.id)
            ?: return

        try {
            driver.quit()
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Failed to quit driver for device '${device.id}'",
                exception
            )
        }
    }

    fun quitAllDrivers() {
        val failures = mutableListOf<Throwable>()

        drivers.entries
            .toList()
            .forEach { (deviceId, driver) ->
                try {
                    driver.quit()
                } catch (exception: Exception) {
                    failures += IllegalStateException(
                        "Failed to quit driver for device '$deviceId'",
                        exception
                    )
                } finally {
                    drivers.remove(deviceId)
                }
            }

        check(failures.isEmpty()) {
            buildString {
                append("Failed to quit ")
                append(failures.size)
                append(" driver(s): ")

                append(
                    failures.joinToString { failure ->
                        failure.message.orEmpty()
                    }
                )
            }
        }
    }

    companion object {

        fun forAndroid(
            androidDriverFactory: AndroidDriverFactory
        ): DriverManager {
            return DriverManager(
                platformName = "Android",
                supportsDevice = Device::isAndroid,
                driverFactory = { device ->
                    androidDriverFactory
                        .createDriverForDevice(device)
                }
            )
        }

        fun forIos(
            iosDriverFactory: IOSDriverFactory
        ): DriverManager {
            return DriverManager(
                platformName = "iOS",
                supportsDevice = Device::isIos,
                driverFactory = { device ->
                    iosDriverFactory
                        .createDriverForDevice(device)
                }
            )
        }
    }
}