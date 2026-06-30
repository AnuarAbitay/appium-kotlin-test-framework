package io.github.january.appium.config

data class FrameworkConfig(
    val appiumJsPath: String,

    val androidAppPath: String? = null,

    val iosSimulatorAppPath: String? = null,
    val iosRealDeviceAppPath: String? = null,

    val language: String = DEFAULT_LANGUAGE,
    val locale: String = DEFAULT_LOCALE
) {

    init {
        require(appiumJsPath.isNotBlank()) {
            "Appium main.js path must not be blank"
        }

        require(language.isNotBlank()) {
            "Language must not be blank"
        }

        require(locale.isNotBlank()) {
            "Locale must not be blank"
        }
    }

    fun requireAndroidAppPath(): String {
        return requireNotNull(
            androidAppPath?.takeIf(String::isNotBlank)
        ) {
            "Android application path is not configured"
        }
    }

    fun requireIosSimulatorAppPath(): String {
        return requireNotNull(
            iosSimulatorAppPath?.takeIf(String::isNotBlank)
        ) {
            "iOS Simulator application path is not configured"
        }
    }

    fun requireIosRealDeviceAppPath(): String {
        return requireNotNull(
            iosRealDeviceAppPath?.takeIf(String::isNotBlank)
        ) {
            "iOS real device application path is not configured"
        }
    }

    private companion object {
        const val DEFAULT_LANGUAGE = "ru"
        const val DEFAULT_LOCALE = "RU"
    }
}