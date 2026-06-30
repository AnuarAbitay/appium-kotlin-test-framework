package io.github.january.appium.device.platforms

import io.github.january.appium.command.CommandExecutor
import io.github.january.appium.device.data.AdbDeviceState
import io.github.january.appium.device.data.AdbDeviceState.ABSENT
import io.github.january.appium.device.data.AdbDeviceState.OFFLINE
import io.github.january.appium.device.data.AdbDeviceState.ONLINE
import io.github.january.appium.device.data.AdbDeviceState.UNAUTHORIZED
import io.github.january.appium.device.data.AdbDeviceState.UNKNOWN
import io.github.january.appium.device.data.Device
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object AndroidEmulatorManager {

    fun bootEmulators(
        devices: List<Device>
    ) = runBlocking {
        val androidEmulators = devices.filter { device ->
            device.isAndroid && device.isEmulator
        }

        if (androidEmulators.isEmpty()) {
            return@runBlocking
        }

        shutdownEmulatorsInternal(androidEmulators)

        androidEmulators
            .map { device ->
                async(IO) {
                    startAndPrepareEmulator(device)
                }
            }
            .awaitAll()
    }

    fun shutdownEmulators(
        devices: List<Device>
    ) = runBlocking {
        val androidEmulators = devices.filter { device ->
            device.isAndroid && device.isEmulator
        }

        shutdownEmulatorsInternal(androidEmulators)
    }

    fun waitForAvdReady(
        udid: String,
        timeout: Duration = 2.minutes
    ) = runBlocking {
        waitUntilReady(
            udid = udid,
            timeout = timeout,
            pollInterval = 1.seconds
        )
    }

    private suspend fun shutdownEmulatorsInternal(
        devices: List<Device>
    ) = coroutineScope {
        devices
            .map { device ->
                async(IO) {
                    shutdownEmulator(device)
                }
            }
            .awaitAll()
    }

    private suspend fun startAndPrepareEmulator(
        device: Device
    ) {
        check(getAdbDeviceState(device.udid) == ABSENT) {
            "Android emulator '${device.id}' is still visible in ADB " +
                    "before startup"
        }

        startEmulator(device)

        waitUntilReady(
            udid = device.udid,
            timeout = BOOT_TIMEOUT,
            pollInterval = BOOT_POLL_INTERVAL
        )

        setDeviceName(
            udid = device.udid,
            name = device.deviceName
        )

        prepareDevice(device.udid)
        resetNetworkViaWifi(device.udid)
    }

    private suspend fun shutdownEmulator(
        device: Device
    ) {
        val currentState = getAdbDeviceState(device.udid)

        if (currentState == ABSENT) {
            return
        }

        val shutdownResult = CommandExecutor.execute(
            "adb",
            "-s",
            device.udid,
            "emu",
            "kill",
            timeout = EMULATOR_KILL_COMMAND_TIMEOUT
        )

        val stateAfterCommand = getAdbDeviceState(device.udid)

        check(
            shutdownResult.isSuccessful ||
                    stateAfterCommand == ABSENT
        ) {
            "Failed to stop Android emulator '${device.id}'. " +
                    "ADB state: $currentState. " +
                    "Exit code: ${shutdownResult.exitCode}. " +
                    "Output: ${shutdownResult.output}"
        }

        val disconnected = waitUntil(
            timeout = SHUTDOWN_TIMEOUT,
            pollInterval = SHUTDOWN_POLL_INTERVAL
        ) {
            getAdbDeviceState(device.udid) == ABSENT
        }

        check(disconnected) {
            "Android emulator '${device.id}' did not disconnect " +
                    "within $SHUTDOWN_TIMEOUT"
        }
    }

    private fun startEmulator(
        device: Device
    ) {
        val avdName = device.deviceName

        val consolePort = requireNotNull(device.avdPort) {
            "avdPort is required for Android emulator '${device.id}'"
        }

        val adbPort = consolePort + 1

        CommandExecutor.startInBackground(
            "emulator",
            "-avd",
            avdName,

            "-ports",
            "$consolePort,$adbPort",

            "-no-snapshot-load",
            "-no-snapshot-save",
            "-no-window",

            "-gpu",
            "swiftshader_indirect",

            "-feature",
            "-Vulkan",

            "-wipe-data",
            "-no-audio",
            "-no-boot-anim",

            "-camera-back",
            "none",

            "-camera-front",
            "none",

            "-netspeed",
            "full",

            "-netdelay",
            "none",

            "-skip-adb-auth",

            "-memory",
            "2048",

            "-cores",
            "2"
        )
    }

    private suspend fun waitUntilReady(
        udid: String,
        timeout: Duration,
        pollInterval: Duration
    ) {
        val isReady = waitUntil(
            timeout = timeout,
            pollInterval = pollInterval
        ) {
            isDeviceReady(udid)
        }

        check(isReady) {
            "Android emulator '$udid' was not ready within $timeout. " +
                    "Current ADB state: ${getAdbDeviceState(udid)}"
        }
    }

    private fun isDeviceReady(
        udid: String
    ): Boolean {
        return isAdbConnected(udid) &&
                isBootCompleted(udid) &&
                isPackageManagerReady(udid)
    }

    private fun isAdbConnected(
        udid: String
    ): Boolean {
        val result = CommandExecutor.execute(
            "adb",
            "-s",
            udid,
            "get-state"
        )

        return result.isSuccessful &&
                result.output.trim() == "device"
    }

    private fun isBootCompleted(
        udid: String
    ): Boolean {
        val result = CommandExecutor.execute(
            "adb",
            "-s",
            udid,
            "shell",
            "getprop",
            "sys.boot_completed"
        )

        return result.isSuccessful &&
                result.output.trim() == "1"
    }

    private fun isPackageManagerReady(
        udid: String
    ): Boolean {
        val result = CommandExecutor.execute(
            "adb",
            "-s",
            udid,
            "shell",
            "pm",
            "path",
            "android"
        )

        return result.isSuccessful &&
                result.output.contains("package:")
    }

    private fun getAdbDeviceState(
        udid: String
    ): AdbDeviceState {
        val result = CommandExecutor.execute(
            "adb",
            "devices"
        )

        check(result.isSuccessful) {
            "Failed to get Android devices. " +
                    "Exit code: ${result.exitCode}. " +
                    "Output: ${result.output}"
        }

        val deviceLine = result.output
            .lineSequence()
            .map(String::trim)
            .firstOrNull { line ->
                line.startsWith("$udid\t") ||
                        line.startsWith("$udid ")
            }
            ?: return ABSENT

        val state = deviceLine
            .removePrefix(udid)
            .trim()
            .substringBefore(' ')
            .lowercase()

        return when (state) {
            "device" -> ONLINE
            "offline" -> OFFLINE
            "unauthorized" -> UNAUTHORIZED
            else -> UNKNOWN
        }
    }

    private fun setDeviceName(
        udid: String,
        name: String
    ) {
        val result = CommandExecutor.execute(
            "adb",
            "-s",
            udid,
            "shell",
            "settings",
            "put",
            "global",
            "device_name",
            name
        )

        check(result.isSuccessful) {
            "Failed to set device name '$name' for '$udid'. " +
                    "Exit code: ${result.exitCode}. " +
                    "Output: ${result.output}"
        }
    }

    private fun prepareDevice(
        udid: String
    ) {
        val commands = listOf(
            listOf(
                "shell",
                "settings",
                "put",
                "global",
                "window_animation_scale",
                "0"
            ),
            listOf(
                "shell",
                "settings",
                "put",
                "global",
                "transition_animation_scale",
                "0"
            ),
            listOf(
                "shell",
                "settings",
                "put",
                "global",
                "animator_duration_scale",
                "0"
            ),
            listOf(
                "shell",
                "settings",
                "put",
                "system",
                "screen_brightness_mode",
                "0"
            ),
            listOf(
                "shell",
                "settings",
                "put",
                "system",
                "screen_brightness",
                "255"
            ),
            listOf(
                "shell",
                "settings",
                "put",
                "global",
                "stay_on_while_plugged_in",
                "3"
            ),
            listOf(
                "shell",
                "settings",
                "put",
                "system",
                "screen_off_timeout",
                "2147483647"
            ),
            listOf(
                "shell",
                "logcat",
                "-G",
                "2M"
            ),
            listOf(
                "shell",
                "settings",
                "put",
                "system",
                "accelerometer_rotation",
                "1"
            )
        )

        commands.forEach { command ->
            CommandExecutor.execute(
                "adb",
                "-s",
                udid,
                *command.toTypedArray()
            )
        }
    }

    private suspend fun resetNetworkViaWifi(
        udid: String
    ) {
        val disableResult = CommandExecutor.execute(
            "adb",
            "-s",
            udid,
            "shell",
            "svc",
            "wifi",
            "disable"
        )

        if (!disableResult.isSuccessful) {
            return
        }

        if (!waitForWifiStatus(
                udid = udid,
                expectedStatus = "disabled"
            )
        ) {
            return
        }

        val enableResult = CommandExecutor.execute(
            "adb",
            "-s",
            udid,
            "shell",
            "svc",
            "wifi",
            "enable"
        )

        if (!enableResult.isSuccessful) {
            return
        }

        if (!waitForWifiStatus(
                udid = udid,
                expectedStatus = "enabled"
            )
        ) {
            return
        }

        waitForWifiConnection(udid)
    }

    private suspend fun waitForWifiStatus(
        udid: String,
        expectedStatus: String,
        timeout: Duration = 5.seconds
    ): Boolean {
        return waitUntil(timeout) {
            val result = CommandExecutor.execute(
                "adb",
                "-s",
                udid,
                "shell",
                "dumpsys",
                "wifi"
            )

            if (!result.isSuccessful) {
                return@waitUntil false
            }

            result.output
                .lineSequence()
                .firstOrNull { line ->
                    line.contains("Wi-Fi is")
                }
                ?.contains(
                    expectedStatus,
                    ignoreCase = true
                ) == true
        }
    }

    private fun getConnectedSsid(
        udid: String
    ): String? {
        val result = CommandExecutor.execute(
            "adb",
            "-s",
            udid,
            "shell",
            "dumpsys",
            "netstats"
        )

        if (!result.isSuccessful) {
            return null
        }

        val networkLine = result.output
            .lineSequence()
            .firstOrNull { line ->
                line.contains("iface=wlan0") &&
                        line.contains("wifiNetworkKey=")
            }
            ?: return null

        return Regex("""wifiNetworkKey="([^"]+)"""")
            .find(networkLine)
            ?.groupValues
            ?.getOrNull(1)
    }

    private suspend fun waitForWifiConnection(
        udid: String,
        timeout: Duration = 5.seconds
    ): Boolean {
        return waitUntil(timeout) {
            !getConnectedSsid(udid).isNullOrBlank()
        }
    }

    private suspend fun waitUntil(
        timeout: Duration,
        pollInterval: Duration = 500.milliseconds,
        condition: () -> Boolean
    ): Boolean {
        val deadline =
            System.nanoTime() + timeout.inWholeNanoseconds

        while (System.nanoTime() < deadline) {
            if (condition()) {
                return true
            }

            delay(pollInterval)
        }

        return condition()
    }

    private val BOOT_TIMEOUT = 4.minutes
    private val BOOT_POLL_INTERVAL = 2.seconds

    private val SHUTDOWN_TIMEOUT = 30.seconds
    private val SHUTDOWN_POLL_INTERVAL = 500.milliseconds
    private val EMULATOR_KILL_COMMAND_TIMEOUT = 15.seconds
}