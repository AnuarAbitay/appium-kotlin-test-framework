package io.github.january.appium.driver.options

import io.appium.java_client.ios.options.XCUITestOptions
import io.github.january.appium.device.data.Device
import java.io.File
import java.time.Duration

class IOSOptionsFactory(
    private val simulatorAppPath: String,
    private val realDeviceAppPath: String,
    private val language: String,
    private val locale: String
) {

    init {
        require(language.isNotBlank()) {
            "iOS language must not be blank"
        }

        require(locale.isNotBlank()) {
            "iOS locale must not be blank"
        }
    }

    fun create(device: Device): XCUITestOptions {
        require(device.isIos) {
            "Device '${device.id}' is not an iOS device"
        }

        val app = resolveApplication(device)

        val bundleId = requireNotNull(device.bundleId) {
            "bundleId is required for iOS device '${device.id}'"
        }.also { value ->
            require(value.isNotBlank()) {
                "bundleId must not be blank for iOS device '${device.id}'"
            }
        }

        val wdaLocalPort = requireNotNull(device.wdaLocalPort) {
            "wdaLocalPort is required for iOS device '${device.id}'"
        }

        return XCUITestOptions()
            .setPlatformName(device.platformName)
            .setPlatformVersion(device.platformVersion)
            .setDeviceName(device.deviceName)
            .setAutomationName(device.automationName)
            .setUdid(device.udid)

            .setApp(app.absolutePath)
            .setBundleId(bundleId)

            .setLanguage(language)
            .setLocale(normalizeLocale())

            .setWdaLocalPort(wdaLocalPort)
            .setNewCommandTimeout(NEW_COMMAND_TIMEOUT)
    }

    private fun resolveApplication(device: Device): File {
        val configuredPath = if (device.isEmulator) {
            simulatorAppPath
        } else {
            realDeviceAppPath
        }

        val deviceType = if (device.isEmulator) {
            "iOS Simulator"
        } else {
            "iOS real device"
        }

        require(configuredPath.isNotBlank()) {
            "$deviceType application path is not configured"
        }

        val app = File(configuredPath).absoluteFile

        require(app.exists()) {
            "$deviceType application was not found: ${app.path}"
        }

        return app
    }

    private fun normalizeLocale(): String {
        val normalizedLanguage = language
            .trim()
            .lowercase()

        val normalizedLocale = locale
            .trim()
            .replace('-', '_')

        return if ('_' in normalizedLocale) {
            normalizedLocale
        } else {
            "${normalizedLanguage}_${normalizedLocale.uppercase()}"
        }
    }

    private companion object {
        val NEW_COMMAND_TIMEOUT: Duration =
            Duration.ofMinutes(30)
    }
}