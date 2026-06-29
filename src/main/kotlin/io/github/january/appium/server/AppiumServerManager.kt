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

    fun startServersForDevices(devices: List<Device>) {
        validateDevices(devices)

        try {
            devices.forEach { device ->
                startServerForDevice(device)
            }
        } catch (exception: Exception) {
            stopAllServers()
            throw exception
        }
    }

    fun startServerForDevice(
        device: Device
    ): AppiumDriverLocalService {
        val existingServer = servers[device.id]

        if (existingServer?.isRunning == true) {
            return existingServer
        }

        if (existingServer != null) {
            servers.remove(device.id)
        }

        val server = serverFactory.createAndStartServer(device)

        servers[device.id] = server

        return server
    }

    fun getServerForDevice(
        device: Device
    ): AppiumDriverLocalService {
        return servers[device.id]
            ?: error(
                "Appium server for device '${device.id}' " +
                        "has not been started"
            )
    }

    fun getServerUrlForDevice(device: Device): URL {
        val server = getServerForDevice(device)

        check(server.isRunning) {
            "Appium server for device '${device.id}' is not running"
        }

        return server.url
    }

    fun stopServerForDevice(device: Device) {
        val server = servers.remove(device.id)
            ?: return

        serverFactory.stopServerInstance(server)
    }

    fun stopAllServers() {
        val failures = mutableListOf<Throwable>()

        servers.entries
            .toList()
            .forEach { (deviceId, server) ->
                try {
                    serverFactory.stopServerInstance(server)
                } catch (exception: Exception) {
                    failures += IllegalStateException(
                        "Failed to stop Appium server " +
                                "for device '$deviceId'",
                        exception
                    )
                } finally {
                    servers.remove(deviceId)
                }
            }

        check(failures.isEmpty()) {
            buildString {
                append("Failed to stop ")
                append(failures.size)
                append(" Appium server(s): ")

                append(
                    failures.joinToString { failure ->
                        failure.message.orEmpty()
                    }
                )
            }
        }
    }

    fun isServerRunningForDevice(device: Device): Boolean {
        return servers[device.id]?.isRunning == true
    }

    private fun validateDevices(devices: List<Device>) {
        val duplicatedIds = devices
            .groupBy(Device::id)
            .filterValues { it.size > 1 }
            .keys

        require(duplicatedIds.isEmpty()) {
            "Duplicate device ids: ${duplicatedIds.joinToString()}"
        }

        val duplicatedPorts = devices
            .groupBy(Device::serverPort)
            .filterValues { it.size > 1 }
            .keys

        require(duplicatedPorts.isEmpty()) {
            "Duplicate Appium server ports: " +
                    duplicatedPorts.joinToString()
        }
    }
}