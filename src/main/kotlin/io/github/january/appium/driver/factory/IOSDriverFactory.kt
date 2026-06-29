package io.github.january.appium.driver.factory

import io.appium.java_client.ios.IOSDriver
import io.github.january.appium.device.data.Device
import io.github.january.appium.driver.options.ios.IOSOptionsFactory
import io.github.january.appium.server.AppiumServerManager

class IOSDriverFactory(
    private val optionsFactory: IOSOptionsFactory,
    private val serverManager: AppiumServerManager
) {

    fun createDriverForDevice(
        device: Device
    ): IOSDriver {
        require(device.isIos) {
            "Device '${device.id}' is not an iOS device"
        }

        check(serverManager.isServerRunningForDevice(device)) {
            "Appium server for iOS device '${device.id}' " +
                    "is not running"
        }

        val options = optionsFactory.create(device)

        return try {
            IOSDriver(
                serverManager.getServerUrlForDevice(device),
                options
            )
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Failed to create iOS driver " +
                        "for device '${device.id}'",
                exception
            )
        }
    }
}