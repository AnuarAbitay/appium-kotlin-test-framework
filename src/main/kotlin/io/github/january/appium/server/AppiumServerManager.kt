package io.github.january.appium.server

import io.appium.java_client.service.local.AppiumDriverLocalService
import io.github.january.appium.device.data.Device
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class AppiumServerManager(
    private val serverFactory: AppiumServerFactory
) {

    private val servers =
        ConcurrentHashMap<String, AppiumDriverLocalService>()

    private val lifecycleLock = Any()

    fun startServersForDevices(
        devices: List<Device>
    ) {
        require(devices.isNotEmpty()) {
            "At least one device is required to start Appium servers"
        }

        validateDevices(devices)

        synchronized(lifecycleLock) {
            val startedDeviceIds = mutableListOf<String>()

            try {
                devices.forEach { device ->
                    val alreadyRunning =
                        servers[device.id]?.isRunning == true

                    if (!alreadyRunning) {
                        startServerForDeviceInternal(device)
                        startedDeviceIds += device.id
                    }
                }
            } catch (startException: Exception) {
                rollbackStartedServers(
                    deviceIds = startedDeviceIds,
                    startException = startException
                )

                throw startException
            }
        }
    }

    fun startServerForDevice(
        device: Device
    ): AppiumDriverLocalService {
        return synchronized(lifecycleLock) {
            startServerForDeviceInternal(device)
        }
    }

    fun getServerForDevice(
        device: Device
    ): AppiumDriverLocalService {
        val server = servers[device.id]
            ?: error(
                "Appium server for device '${device.id}' " +
                        "has not been started"
            )

        check(server.isRunning) {
            "Appium server for device '${device.id}' is not running"
        }

        return server
    }

    fun getServerUrlForDevice(
        device: Device
    ): URL {
        return getServerForDevice(device).url
    }

    fun isServerRunningForDevice(
        device: Device
    ): Boolean {
        return servers[device.id]?.isRunning == true
    }

    fun stopServerForDevice(
        device: Device
    ) {
        synchronized(lifecycleLock) {
            stopServerForDeviceInternal(device.id)
        }
    }

    fun stopAllServers() {
        synchronized(lifecycleLock) {
            val failures = mutableListOf<Throwable>()

            servers.keys
                .toList()
                .asReversed()
                .forEach { deviceId ->
                    try {
                        stopServerForDeviceInternal(deviceId)
                    } catch (exception: Exception) {
                        failures += IllegalStateException(
                            "Failed to stop Appium server " +
                                    "for device '$deviceId'",
                            exception
                        )
                    }
                }

            if (failures.isNotEmpty()) {
                val exception = IllegalStateException(
                    "Failed to stop ${failures.size} Appium server(s)"
                )

                failures.forEach(exception::addSuppressed)

                throw exception
            }
        }
    }

    private fun startServerForDeviceInternal(
        device: Device
    ): AppiumDriverLocalService {
        val existingServer = servers[device.id]

        if (existingServer?.isRunning == true) {
            return existingServer
        }

        if (existingServer != null) {
            servers.remove(device.id, existingServer)
        }

        val server = serverFactory.createAndStartServer(device)

        servers[device.id] = server

        return server
    }

    private fun stopServerForDeviceInternal(
        deviceId: String
    ) {
        val server = servers[deviceId]
            ?: return

        serverFactory.stopServerInstance(server)

        servers.remove(deviceId, server)
    }

    private fun rollbackStartedServers(
        deviceIds: List<String>,
        startException: Exception
    ) {
        deviceIds
            .asReversed()
            .forEach { deviceId ->
                try {
                    stopServerForDeviceInternal(deviceId)
                } catch (cleanupException: Exception) {
                    startException.addSuppressed(cleanupException)
                }
            }
    }

    private fun validateDevices(
        devices: List<Device>
    ) {
        val duplicateIds = devices
            .groupingBy(Device::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

        require(duplicateIds.isEmpty()) {
            "Duplicate device ids: ${duplicateIds.joinToString()}"
        }

        val duplicatePorts = devices
            .groupingBy(Device::serverPort)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

        require(duplicatePorts.isEmpty()) {
            "Duplicate Appium server ports: " +
                    duplicatePorts.joinToString()
        }
    }
}