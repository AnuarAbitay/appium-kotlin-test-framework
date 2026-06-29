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
                startServer(device)
            }
        } catch (exception: Exception) {
            stopServers()
            throw exception
        }
    }

    fun startServer(
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

    fun getServer(
        device: Device
    ): AppiumDriverLocalService {
        return servers[device.id]
            ?: error(
                "Appium server for device '${device.id}' " +
                        "has not been started"
            )
    }

    fun getServerUrl(device: Device): URL {
        val server = getServer(device)

        check(server.isRunning) {
            "Appium server for device '${device.id}' is not running"
        }

        return server.url
    }

    fun stopServer(device: Device) {
        val server = servers.remove(device.id)
            ?: return

        serverFactory.stopServerInstance(server)
    }

    fun stopServers() {
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

    fun isRunning(device: Device): Boolean {
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