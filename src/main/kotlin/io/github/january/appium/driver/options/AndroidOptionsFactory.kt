package io.github.january.appium.driver.options

import io.appium.java_client.android.options.UiAutomator2Options
import io.github.january.appium.device.data.Device
import java.io.File
import java.time.Duration

class AndroidOptionsFactory(
    appPath: String,
    private val language: String,
    private val locale: String
) {

    private val appFile = File(appPath).absoluteFile

    init {
        require(appPath.isNotBlank()) {
            "Android application path must not be blank"
        }

        require(appFile.isFile) {
            "Android application was not found: ${appFile.path}"
        }

        require(language.isNotBlank()) {
            "Android language must not be blank"
        }

        require(locale.isNotBlank()) {
            "Android locale must not be blank"
        }
    }

    fun create(device: Device): UiAutomator2Options {
        require(device.isAndroid) {
            "Device '${device.id}' is not an Android device"
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
            .setPlatformName(device.platformName)
            .setPlatformVersion(device.platformVersion)
            .setDeviceName(device.deviceName)
            .setUdid(device.udid)
            .setAutomationName(device.automationName)

            .setApp(appFile.path)
            .setAppPackage(appPackage)
            .setAppActivity(appActivity)
            .setAppWaitPackage(appPackage)
            .setAppWaitActivity(appActivity)
            .setAppWaitForLaunch(true)
            .setAppWaitDuration(APP_WAIT_TIMEOUT)

            .setLanguage(language)
            .setLocale(locale)

            .setAutoGrantPermissions(true)

            .setAdbExecTimeout(ADB_EXEC_TIMEOUT)
            .setAndroidInstallTimeout(APP_INSTALL_TIMEOUT)
            .setUiautomator2ServerLaunchTimeout(
                UIAUTOMATOR_SERVER_LAUNCH_TIMEOUT
            )
            .setUiautomator2ServerInstallTimeout(
                UIAUTOMATOR_SERVER_INSTALL_TIMEOUT
            )
            .setNewCommandTimeout(NEW_COMMAND_TIMEOUT)

            .setSystemPort(systemPort)
            .setChromedriverPort(chromedriverPort)
    }

    private companion object {
        val APP_WAIT_TIMEOUT: Duration =
            Duration.ofSeconds(60)

        val ADB_EXEC_TIMEOUT: Duration =
            Duration.ofSeconds(180)

        val APP_INSTALL_TIMEOUT: Duration =
            Duration.ofSeconds(300)

        val UIAUTOMATOR_SERVER_LAUNCH_TIMEOUT: Duration =
            Duration.ofSeconds(120)

        val UIAUTOMATOR_SERVER_INSTALL_TIMEOUT: Duration =
            Duration.ofSeconds(120)

        val NEW_COMMAND_TIMEOUT: Duration =
            Duration.ofMinutes(30)
    }
}