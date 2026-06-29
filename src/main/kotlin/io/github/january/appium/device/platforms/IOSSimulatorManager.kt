package io.github.january.appium.device.platforms

import io.github.january.appium.command.CommandExecutor
import io.github.january.appium.device.data.Device
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

object IOSSimulatorManager {

    fun bootSimulators(devices: List<Device>) = runBlocking {
        val iosSimulators = devices.filter {
            it.isIos && it.isEmulator
        }

        val jobs = iosSimulators.map { device ->
            async(IO) {
                if (!isSimulatorBooted(device.udid)) {
                    val result = CommandExecutor.execute(
                        "xcrun",
                        "simctl",
                        "boot",
                        device.udid
                    )

                    check(result.isSuccessful) {
                        "Failed to boot iOS simulator '${device.id}'. " +
                                "Exit code: ${result.exitCode}. " +
                                "Output: ${result.output}"
                    }
                }

                waitUntilBooted(device)
            }
        }

        withTimeout(2.minutes) {
            jobs.awaitAll()
        }
    }

    fun shutdownSimulators(devices: List<Device>) {
        devices
            .filter { it.isIos && it.isEmulator }
            .forEach { device ->
                val result = CommandExecutor.execute(
                    "xcrun",
                    "simctl",
                    "shutdown",
                    device.udid
                )

                val alreadyShutdown = result.output.contains(
                    "current state: Shutdown",
                    ignoreCase = true
                )

                check(result.isSuccessful || alreadyShutdown) {
                    "Failed to shutdown iOS simulator '${device.id}'. " +
                            "Exit code: ${result.exitCode}. " +
                            "Output: ${result.output}"
                }
            }
    }

    fun closeSimulatorApp() {
        val result = CommandExecutor.execute(
            "killall",
            "Simulator"
        )

        check(result.isSuccessful || result.exitCode == 1) {
            "Failed to close Simulator application. " +
                    "Exit code: ${result.exitCode}. " +
                    "Output: ${result.output}"
        }
    }

    private fun isSimulatorBooted(udid: String): Boolean {
        val result = CommandExecutor.execute(
            "xcrun",
            "simctl",
            "list",
            "devices"
        )

        check(result.isSuccessful) {
            "Failed to get iOS simulator list. " +
                    "Exit code: ${result.exitCode}. " +
                    "Output: ${result.output}"
        }

        return result.output
            .lineSequence()
            .any { line ->
                line.contains(udid) &&
                        line.contains("(Booted)")
            }
    }

    private fun waitUntilBooted(device: Device) {
        val result = CommandExecutor.execute(
            "xcrun",
            "simctl",
            "bootstatus",
            device.udid,
            "-b",
            timeout = 2.minutes
        )

        check(result.isSuccessful) {
            "iOS simulator '${device.id}' did not finish booting. " +
                    "Exit code: ${result.exitCode}. " +
                    "Output: ${result.output}"
        }
    }
}