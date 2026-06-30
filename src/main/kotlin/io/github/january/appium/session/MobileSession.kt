package io.github.january.appium.session

import io.appium.java_client.AppiumDriver
import io.github.january.appium.device.data.Device

data class MobileSession(
    val device: Device,
    val driver: AppiumDriver
)