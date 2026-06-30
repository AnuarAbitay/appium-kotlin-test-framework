package io.github.january.appium.server

import io.appium.java_client.service.local.AppiumDriverLocalService
import io.appium.java_client.service.local.AppiumServiceBuilder
import io.appium.java_client.service.local.flags.GeneralServerFlag
import io.github.january.appium.device.data.Device
import java.io.File
import java.net.ServerSocket
import java.time.Duration

class AppiumServerFactory(
    appiumJsPath: String
) {

    private val appiumJsFile = File(appiumJsPath)

    init {
        require(appiumJsPath.isNotBlank()) {
            "Appium main.js path must not be blank"
        }

        require(appiumJsFile.isFile) {
            "Appium main.js was not found: " +
                    appiumJsFile.absolutePath
        }
    }

    fun createAndStartServer(
        device: Device
    ): AppiumDriverLocalService {
        require(isPortAvailable(device.serverPort)) {
            "Appium server port ${device.serverPort} " +
                    "for device '${device.id}' is already in use"
        }

        val service = AppiumDriverLocalService.buildService(
            createServiceBuilder(device)
        )

        try {
            service.start()

            check(service.isRunning) {
                "Appium server for device '${device.id}' " +
                        "did not start on port ${device.serverPort}"
            }

            return service
        } catch (exception: Exception) {
            runCatching {
                service.stop()
            }

            throw IllegalStateException(
                "Failed to start Appium server for device " +
                        "'${device.id}' on port ${device.serverPort}",
                exception
            )
        }
    }

    fun stopServerInstance(
        server: AppiumDriverLocalService
    ) {
        if (!server.isRunning) {
            return
        }

        try {
            server.stop()
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Failed to stop Appium server " +
                        "on port ${server.url.port}",
                exception
            )
        }
    }

    private fun createServiceBuilder(
        device: Device
    ): AppiumServiceBuilder {
        return AppiumServiceBuilder()
            .withIPAddress(LOCALHOST)
            .usingPort(device.serverPort)
            .withAppiumJS(appiumJsFile)
            .withTimeout(
                Duration.ofSeconds(STARTUP_TIMEOUT_SECONDS)
            )
            .withArgument(
                GeneralServerFlag.LOG_LEVEL,
                APPIUM_LOG_LEVEL
            )
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val LOCALHOST = "127.0.0.1"
        const val APPIUM_LOG_LEVEL = "error"
        const val STARTUP_TIMEOUT_SECONDS = 30L
    }
}