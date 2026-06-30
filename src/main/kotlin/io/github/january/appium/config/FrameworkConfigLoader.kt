package io.github.january.appium.config

import java.util.Properties

object FrameworkConfigLoader {

    private val fileProperties: Properties by lazy {
        loadProperties()
    }

    fun load(): FrameworkConfig {
        return FrameworkConfig(
            appiumJsPath = requireValue(
                propertyName = APPIUM_JS_PATH_PROPERTY,
                environmentName = APPIUM_JS_PATH_ENV
            ),
            androidAppPath = getValue(
                propertyName = ANDROID_APP_PATH_PROPERTY,
                environmentName = ANDROID_APP_PATH_ENV
            ),
            iosSimulatorAppPath = getValue(
                propertyName = IOS_SIMULATOR_APP_PATH_PROPERTY,
                environmentName = IOS_SIMULATOR_APP_PATH_ENV
            ),
            iosRealDeviceAppPath = getValue(
                propertyName = IOS_REAL_DEVICE_APP_PATH_PROPERTY,
                environmentName = IOS_REAL_DEVICE_APP_PATH_ENV
            ),
            language = getValue(
                propertyName = LANGUAGE_PROPERTY,
                environmentName = LANGUAGE_ENV
            ) ?: DEFAULT_LANGUAGE,
            locale = getValue(
                propertyName = LOCALE_PROPERTY,
                environmentName = LOCALE_ENV
            ) ?: DEFAULT_LOCALE
        )
    }

    private fun requireValue(
        propertyName: String,
        environmentName: String
    ): String {
        return requireNotNull(
            getValue(
                propertyName = propertyName,
                environmentName = environmentName
            )
        ) {
            "Required configuration is missing. " +
                    "Set system property '$propertyName', " +
                    "environment variable '$environmentName', " +
                    "or property '$environmentName' in $CONFIG_FILE"
        }
    }

    private fun getValue(
        propertyName: String,
        environmentName: String
    ): String? {
        return System.getProperty(propertyName).normalized()
            ?: System.getenv(environmentName).normalized()
            ?: fileProperties
                .getProperty(environmentName)
                .normalized()
    }

    private fun loadProperties(): Properties {
        val properties = Properties()

        val inputStream = FrameworkConfigLoader::class.java
            .classLoader
            .getResourceAsStream(CONFIG_FILE)
            ?: return properties

        inputStream.use { stream ->
            properties.load(stream)
        }

        return properties
    }

    private fun String?.normalized(): String? {
        return this
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private const val CONFIG_FILE =
        "config.properties"

    private const val APPIUM_JS_PATH_PROPERTY =
        "appium.js.path"

    private const val ANDROID_APP_PATH_PROPERTY =
        "android.app.path"

    private const val IOS_SIMULATOR_APP_PATH_PROPERTY =
        "ios.simulator.app.path"

    private const val IOS_REAL_DEVICE_APP_PATH_PROPERTY =
        "ios.real.device.app.path"

    private const val LANGUAGE_PROPERTY =
        "framework.language"

    private const val LOCALE_PROPERTY =
        "framework.locale"

    private const val APPIUM_JS_PATH_ENV =
        "APPIUM_JS_PATH"

    private const val ANDROID_APP_PATH_ENV =
        "APP_ANDROID_DEVICE_PATH"

    private const val IOS_SIMULATOR_APP_PATH_ENV =
        "APP_IOS_EMULATOR_PATH"

    private const val IOS_REAL_DEVICE_APP_PATH_ENV =
        "APP_IOS_REAL_DEVICE_PATH"

    private const val LANGUAGE_ENV =
        "FRAMEWORK_LANGUAGE"

    private const val LOCALE_ENV =
        "FRAMEWORK_LOCALE"

    private const val DEFAULT_LANGUAGE = "ru"
    private const val DEFAULT_LOCALE = "RU"
}