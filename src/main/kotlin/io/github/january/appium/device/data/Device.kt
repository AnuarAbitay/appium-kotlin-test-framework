package io.github.january.appium.device.data

data class Device(
    val id: String,
    val platformName: String,
    val platformVersion: String,
    val deviceName: String,
    val automationName: String,
    val udid: String,
    val serverPort: Int,
    val isEmulator: Boolean = false,

    // Android
    val avdPort: Int? = null,
    val systemPort: Int? = null,
    val chromedriverPort: Int? = null,
    val appPackage: String? = null,
    val appActivity: String? = null,

    // iOS
    val wdaLocalPort: Int? = null,
    val bundleId: String? = null
) {
    val isAndroid: Boolean
        get() = platformName.equals("Android", ignoreCase = true)

    val isIos: Boolean
        get() = platformName.equals("iOS", ignoreCase = true)
}
