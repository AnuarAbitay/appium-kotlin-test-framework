package io.github.january.appium.driver.options.android

import io.appium.java_client.android.options.UiAutomator2Options
import io.github.january.appium.device.data.Device
import java.io.File
import java.time.Duration

class AndroidOptionsFactory(
    private val appPath: String
) {

    fun create(
        device: Device,
        language: String = DEFAULT_LANGUAGE,
        locale: String = DEFAULT_LOCALE
    ): UiAutomator2Options {
        require(device.isAndroid) {
            "Device '${device.id}' is not an Android device"
        }

        val appFile = File(appPath)

        require(appFile.isFile) {
            "Android application was not found: $appPath"
        }

        val appPackage = requireNotNull(device.appPackage) {
            "appPackage is required for Android device '${device.id}'"
        }

        val appActivity = requireNotNull(device.appActivity) {
            "appActivity is required for Android device '${device.id}'"
        }

        val systemPort = requireNotNull(device.systemPort) {
            "systemPort is required for Android device '${device.id}'"
        }

        val chromedriverPort = requireNotNull(device.chromedriverPort) {
            "chromedriverPort is required for Android device '${device.id}'"
        }

        return UiAutomator2Options()
            // Language
            .setLanguage(language)
            .setLocale(locale)

            // Application
            .setApp(appFile.absolutePath)
            .setAppPackage(appPackage)
            .setAppActivity(appActivity)
            .setAppWaitPackage(appPackage)
            .setAppWaitActivity(appActivity)
            .setAppWaitForLaunch(true)
            .setAppWaitDuration(Duration.ofSeconds(60))

            // Device
            .setPlatformName(device.platformName)
            .setPlatformVersion(device.platformVersion)
            .setDeviceName(device.deviceName)
            .setUdid(device.udid)
            .setAutomationName(device.automationName)

            // Application lifecycle
            .setFullReset(true)
            .setNoReset(false)
            .setAutoGrantPermissions(true)

            // Emulator
            .setGpsEnabled(true)
            .setDisableWindowAnimation(true)
            .setIsHeadless(device.isEmulator)
            .setSkipUnlock(true)
            .setIgnoreHiddenApiPolicyError(true)

            // Timeouts
            .setAdbExecTimeout(Duration.ofSeconds(180))
            .setAndroidInstallTimeout(Duration.ofSeconds(300))
            .setUiautomator2ServerLaunchTimeout(
                Duration.ofSeconds(120)
            )
            .setUiautomator2ServerInstallTimeout(
                Duration.ofSeconds(120)
            )
            .setNewCommandTimeout(Duration.ofSeconds(1_800))

            // Parallel execution
            .setSystemPort(systemPort)
            .setChromedriverPort(chromedriverPort)
    }

    private companion object {
        const val DEFAULT_LANGUAGE = "ru"
        const val DEFAULT_LOCALE = "RU"
    }
}