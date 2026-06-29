package io.github.january.appium.server

import io.appium.java_client.service.local.AppiumDriverLocalService
import io.appium.java_client.service.local.AppiumServiceBuilder
import io.appium.java_client.service.local.flags.GeneralServerFlag
import io.github.january.appium.device.data.Device
import java.io.File
import java.time.Duration

class AppiumServerFactory(
    private val appiumJsPath: String
) {

    fun createAndStartServer(device: Device): AppiumDriverLocalService {
        val service = AppiumDriverLocalService.buildService(
            createServiceBuilder(device)
        )

        return try {
            service.start()

            check(service.isRunning) {
                "Appium server for '${device.id}' did not start " +
                        "on port ${device.serverPort}"
            }

            service
        } catch (exception: Exception) {
            runCatching {
                service.stop()
            }

            throw IllegalStateException(
                "Failed to start Appium server for '${device.id}' " +
                        "on port ${device.serverPort}",
                exception
            )
        }
    }

    fun stopServerInstance(server: AppiumDriverLocalService) {
        if (!server.isRunning) {
            return
        }

        try {
            server.stop()
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Failed to stop Appium server on port ${server.url.port}",
                exception
            )
        }
    }

    private fun createServiceBuilder(
        device: Device
    ): AppiumServiceBuilder {
        val appiumJsFile = File(appiumJsPath)

        require(appiumJsFile.isFile) {
            "Appium main.js was not found: $appiumJsPath"
        }

        return AppiumServiceBuilder()
            .withIPAddress(LOCALHOST)
            .usingPort(device.serverPort)
            .withAppiumJS(appiumJsFile)
            .withTimeout(
                Duration.ofSeconds(STARTUP_TIMEOUT_SECONDS)
            )
            .withArgument(GeneralServerFlag.RELAXED_SECURITY)
            .withArgument(
                GeneralServerFlag.LOG_LEVEL,
                APPIUM_LOG_LEVEL
            )
    }

    private companion object {
        const val LOCALHOST = "127.0.0.1"
        const val APPIUM_LOG_LEVEL = "error"
        const val STARTUP_TIMEOUT_SECONDS = 30L
    }
}