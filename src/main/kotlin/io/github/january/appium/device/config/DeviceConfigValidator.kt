package io.github.january.appium.device.config

import io.github.january.appium.device.data.Device
import io.github.january.appium.device.data.DeviceConfig

object DeviceConfigValidator {

    fun validate(config: DeviceConfig) {
        val devices = config.devices

        require(devices.isNotEmpty()) {
            "At least one device must be configured"
        }

        validateUniqueIds(devices)
        validateUniqueUdids(devices)

        devices.forEach(::validateDevice)

        validateUniquePorts(devices)
    }

    private fun validateDevice(device: Device) {
        require(device.id.isNotBlank()) {
            "Device id must not be blank"
        }

        require(device.platformName.isNotBlank()) {
            "platformName must not be blank for device '${device.id}'"
        }

        require(device.platformVersion.isNotBlank()) {
            "platformVersion must not be blank for device '${device.id}'"
        }

        require(device.deviceName.isNotBlank()) {
            "deviceName must not be blank for device '${device.id}'"
        }

        require(device.automationName.isNotBlank()) {
            "automationName must not be blank for device '${device.id}'"
        }

        require(device.udid.isNotBlank()) {
            "udid must not be blank for device '${device.id}'"
        }

        validatePort(
            deviceId = device.id,
            fieldName = "serverPort",
            port = device.serverPort
        )

        require(device.isAndroid || device.isIos) {
            "Unsupported platformName='${device.platformName}' " +
                    "for device '${device.id}'. " +
                    "Supported platforms: Android, iOS"
        }

        when {
            device.isAndroid -> validateAndroidDevice(device)
            device.isIos -> validateIosDevice(device)
        }
    }

    private fun validateAndroidDevice(device: Device) {
        require(
            device.automationName.equals(
                "UiAutomator2",
                ignoreCase = true
            )
        ) {
            "Android device '${device.id}' must use " +
                    "automationName='UiAutomator2'"
        }

        requireNotNull(device.systemPort) {
            "systemPort is required for Android device '${device.id}'"
        }.also { port ->
            validatePort(
                deviceId = device.id,
                fieldName = "systemPort",
                port = port
            )
        }

        requireNotNull(device.chromedriverPort) {
            "chromedriverPort is required for Android device '${device.id}'"
        }.also { port ->
            validatePort(
                deviceId = device.id,
                fieldName = "chromedriverPort",
                port = port
            )
        }

        require(!device.appPackage.isNullOrBlank()) {
            "appPackage is required for Android device '${device.id}'"
        }

        require(!device.appActivity.isNullOrBlank()) {
            "appActivity is required for Android device '${device.id}'"
        }

        require(device.wdaLocalPort == null) {
            "wdaLocalPort must not be configured for Android device '${device.id}'"
        }

        require(device.bundleId == null) {
            "bundleId must not be configured for Android device '${device.id}'"
        }

        if (device.isEmulator) {
            validateAndroidEmulator(device)
        } else {
            require(device.avdPort == null) {
                "avdPort must not be configured for real Android device '${device.id}'"
            }
        }
    }

    private fun validateAndroidEmulator(device: Device) {
        val avdPort = requireNotNull(device.avdPort) {
            "avdPort is required for Android emulator '${device.id}'"
        }

        validatePort(
            deviceId = device.id,
            fieldName = "avdPort",
            port = avdPort
        )

        require(avdPort < 65535) {
            "avdPort=$avdPort is invalid for Android emulator " +
                    "'${device.id}' because port ${avdPort + 1} is also required"
        }

        require(avdPort % 2 == 0) {
            "avdPort=$avdPort must be an even number " +
                    "for Android emulator '${device.id}'"
        }

        val expectedUdid = "emulator-$avdPort"

        require(device.udid == expectedUdid) {
            "Android emulator '${device.id}' has udid='${device.udid}', " +
                    "but avdPort=$avdPort requires udid='$expectedUdid'"
        }
    }

    private fun validateIosDevice(device: Device) {
        require(
            device.automationName.equals(
                "XCUITest",
                ignoreCase = true
            )
        ) {
            "iOS device '${device.id}' must use " +
                    "automationName='XCUITest'"
        }

        requireNotNull(device.wdaLocalPort) {
            "wdaLocalPort is required for iOS device '${device.id}'"
        }.also { port ->
            validatePort(
                deviceId = device.id,
                fieldName = "wdaLocalPort",
                port = port
            )
        }

        require(!device.bundleId.isNullOrBlank()) {
            "bundleId is required for iOS device '${device.id}'"
        }

        require(device.avdPort == null) {
            "avdPort must not be configured for iOS device '${device.id}'"
        }

        require(device.systemPort == null) {
            "systemPort must not be configured for iOS device '${device.id}'"
        }

        require(device.chromedriverPort == null) {
            "chromedriverPort must not be configured for iOS device '${device.id}'"
        }

        require(device.appPackage == null) {
            "appPackage must not be configured for iOS device '${device.id}'"
        }

        require(device.appActivity == null) {
            "appActivity must not be configured for iOS device '${device.id}'"
        }
    }

    private fun validateUniqueIds(devices: List<Device>) {
        val duplicates = devices
            .groupBy { device -> device.id }
            .filterValues { matchingDevices ->
                matchingDevices.size > 1
            }
            .keys

        require(duplicates.isEmpty()) {
            "Duplicate device ids: ${duplicates.joinToString()}"
        }
    }

    private fun validateUniqueUdids(devices: List<Device>) {
        val duplicates = devices
            .groupBy { device -> device.udid }
            .filterValues { matchingDevices ->
                matchingDevices.size > 1
            }
            .mapValues { (_, matchingDevices) ->
                matchingDevices.map { device -> device.id }
            }

        require(duplicates.isEmpty()) {
            duplicates.entries.joinToString(
                prefix = "Duplicate device UDIDs: "
            ) { (udid, deviceIds) ->
                "'$udid' is used by ${deviceIds.joinToString()}"
            }
        }
    }

    private fun validateUniquePorts(devices: List<Device>) {
        val portUsages = buildList {
            devices.forEach { device ->
                add(
                    PortUsage(
                        port = device.serverPort,
                        deviceId = device.id,
                        fieldName = "serverPort"
                    )
                )

                device.systemPort?.let { port ->
                    add(
                        PortUsage(
                            port = port,
                            deviceId = device.id,
                            fieldName = "systemPort"
                        )
                    )
                }

                device.chromedriverPort?.let { port ->
                    add(
                        PortUsage(
                            port = port,
                            deviceId = device.id,
                            fieldName = "chromedriverPort"
                        )
                    )
                }

                device.wdaLocalPort?.let { port ->
                    add(
                        PortUsage(
                            port = port,
                            deviceId = device.id,
                            fieldName = "wdaLocalPort"
                        )
                    )
                }

                device.avdPort?.let { port ->
                    add(
                        PortUsage(
                            port = port,
                            deviceId = device.id,
                            fieldName = "avdPort"
                        )
                    )

                    add(
                        PortUsage(
                            port = port + 1,
                            deviceId = device.id,
                            fieldName = "adbPort"
                        )
                    )
                }
            }
        }

        val collisions = portUsages
            .groupBy { usage -> usage.port }
            .filterValues { usages -> usages.size > 1 }

        require(collisions.isEmpty()) {
            collisions.entries
                .sortedBy { (port, _) -> port }
                .joinToString(
                    prefix = "Port collisions detected: "
                ) { (port, usages) ->
                    val owners = usages.joinToString { usage ->
                        "${usage.deviceId}.${usage.fieldName}"
                    }

                    "$port is used by $owners"
                }
        }
    }

    private fun validatePort(
        deviceId: String,
        fieldName: String,
        port: Int
    ) {
        require(port in 1..65535) {
            "$fieldName=$port is invalid for device '$deviceId'. " +
                    "Port must be between 1 and 65535"
        }
    }

    private data class PortUsage(
        val port: Int,
        val deviceId: String,
        val fieldName: String
    )
}