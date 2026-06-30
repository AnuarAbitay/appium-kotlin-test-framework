package io.github.january.appium.driver.factory

import io.appium.java_client.android.AndroidDriver
import io.github.january.appium.device.data.Device
import io.github.january.appium.driver.options.AndroidOptionsFactory
import io.github.january.appium.server.AppiumServerManager

class AndroidDriverFactory(
    private val optionsFactory: AndroidOptionsFactory,
    private val serverManager: AppiumServerManager
) {

    fun createDriverForDevice(
        device: Device
    ): AndroidDriver {
        require(device.isAndroid) {
            "Device '${device.id}' is not an Android device"
        }

        check(serverManager.isServerRunningForDevice(device)) {
            "Appium server for Android device '${device.id}' " +
                    "is not running"
        }

        val options = optionsFactory.create(device)
        val serverUrl = serverManager.getServerUrlForDevice(device)

        return try {
            AndroidDriver(
                serverUrl,
                options
            )
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Failed to create Android driver " +
                        "for device '${device.id}' " +
                        "using Appium server '$serverUrl'",
                exception
            )
        }
    }
}