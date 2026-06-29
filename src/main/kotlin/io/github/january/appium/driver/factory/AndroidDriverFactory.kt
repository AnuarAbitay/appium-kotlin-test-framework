package io.github.january.appium.driver.factory

import io.appium.java_client.android.AndroidDriver
import io.github.january.appium.device.data.Device
import io.github.january.appium.driver.options.android.AndroidOptionsFactory
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

        var driver: AndroidDriver? = null

        return try {
            driver = AndroidDriver(
                serverManager.getServerUrlForDevice(device),
                options
            )

            applyRuntimeSettings(driver)

            driver
        } catch (exception: Exception) {
            runCatching {
                driver?.quit()
            }

            throw IllegalStateException(
                "Failed to create Android driver " +
                        "for device '${device.id}'",
                exception
            )
        }
    }

    private fun applyRuntimeSettings(
        driver: AndroidDriver
    ) {
        driver.setSettings(
            mapOf<String, Any>(
                "disableIdLocatorAutocompletion" to true,
                "waitForIdleTimeout" to 0,
                "waitForSelectorTimeout" to 5_000,
                "actionAcknowledgmentTimeout" to 0,
                "ignoreUnimportantViews" to true
            )
        )
    }
}