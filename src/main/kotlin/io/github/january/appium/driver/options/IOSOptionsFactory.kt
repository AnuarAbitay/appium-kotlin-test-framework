package io.github.january.appium.driver.options.ios

import io.appium.java_client.ios.options.XCUITestOptions
import io.github.january.appium.device.data.Device
import java.io.File
import java.time.Duration

class IOSOptionsFactory(
    private val simulatorAppPath: String,
    private val realDeviceAppPath: String
) {

    fun create(
        device: Device,
        language: String = DEFAULT_LANGUAGE,
        locale: String = DEFAULT_LOCALE
    ): XCUITestOptions {
        require(device.isIos) {
            "Device '${device.id}' is not an iOS device"
        }

        val appPath = resolveAppPath(device)
        val app = File(appPath)

        require(app.exists()) {
            "iOS application was not found: $appPath"
        }

        val bundleId = requireNotNull(device.bundleId) {
            "bundleId is required for iOS device '${device.id}'"
        }

        val wdaLocalPort = requireNotNull(device.wdaLocalPort) {
            "wdaLocalPort is required for iOS device '${device.id}'"
        }

        return XCUITestOptions()
            // Device
            .setPlatformName(device.platformName)
            .setPlatformVersion(device.platformVersion)
            .setDeviceName(device.deviceName)
            .setAutomationName(device.automationName)
            .setUdid(device.udid)

            // Application
            .setApp(app.absolutePath)
            .setBundleId(bundleId)

            // Localization
            .setLanguage(language)
            .setLocale(
                normalizeLocale(
                    language = language,
                    locale = locale
                )
            )

            // Application lifecycle
            .setNoReset(false)
            .setFullReset(false)

            // Parallel execution
            .setWdaLocalPort(wdaLocalPort)

            // Session
            .setNewCommandTimeout(Duration.ofMinutes(30))

            // WebDriverAgent
            .amend("appium:useNewWDA", false)
            .amend("appium:wdaLaunchTimeout", 120_000)
            .amend("appium:wdaConnectionTimeout", 240_000)
            .amend("appium:wdaStartupRetries", 4)
            .amend("appium:wdaStartupRetryInterval", 20_000)
    }

    private fun resolveAppPath(device: Device): String {
        return if (device.isEmulator) {
            simulatorAppPath
        } else {
            realDeviceAppPath
        }
    }

    private fun normalizeLocale(
        language: String,
        locale: String
    ): String {
        return if ("_" in locale) {
            locale
        } else {
            "${language.lowercase()}_${locale.uppercase()}"
        }
    }

    private companion object {
        const val DEFAULT_LANGUAGE = "ru"
        const val DEFAULT_LOCALE = "RU"
    }
}