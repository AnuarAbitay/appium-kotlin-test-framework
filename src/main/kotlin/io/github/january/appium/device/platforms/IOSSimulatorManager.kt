package io.github.january.appium.device.platforms

import io.github.january.appium.command.CommandExecutor
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

object IOSSimulatorManager {

    fun bootSimulators(
        devices: List<Device>
    ) = runBlocking {
        val iosSimulators = devices.filter { device ->
            device.isIos && device.isEmulator
        }

        if (iosSimulators.isEmpty()) {
            return@runBlocking
        }

        shutdownSimulatorsInternal(iosSimulators)

        iosSimulators
            .map { device ->
                async(IO) {
                    bootSimulator(device)
                }
            }
            .awaitAll()
    }

    fun shutdownSimulators(
        devices: List<Device>
    ) = runBlocking {
        val iosSimulators = devices.filter { device ->
            device.isIos && device.isEmulator
        }

        shutdownSimulatorsInternal(iosSimulators)
    }

    private suspend fun shutdownSimulatorsInternal(
        devices: List<Device>
    ) = coroutineScope {
        devices
            .map { device ->
                async(IO) {
                    shutdownSimulator(device)
                }
            }
            .awaitAll()
    }

    private fun bootSimulator(
        device: Device
    ) {
        val stateBeforeBoot = getSimulatorState(device)

        check(stateBeforeBoot == SimulatorState.SHUTDOWN) {
            "iOS simulator '${device.id}' must be shutdown before boot, " +
                    "but current state is $stateBeforeBoot"
        }

        val result = CommandExecutor.execute(
            "xcrun",
            "simctl",
            "boot",
            device.udid,
            timeout = BOOT_COMMAND_TIMEOUT
        )

        val stateAfterCommand = getSimulatorState(device)

        check(
            result.isSuccessful ||
                    stateAfterCommand == SimulatorState.BOOTED
        ) {
            "Failed to boot iOS simulator '${device.id}'. " +
                    "Exit code: ${result.exitCode}. " +
                    "Output: ${result.output}"
        }

        waitUntilBooted(device)
    }

    private suspend fun shutdownSimulator(
        device: Device
    ) {
        when (val currentState = getSimulatorState(device)) {
            SimulatorState.ABSENT -> {
                error(
                    "iOS simulator '${device.id}' with UDID " +
                            "'${device.udid}' was not found"
                )
            }

            SimulatorState.SHUTDOWN -> {
                return
            }

            SimulatorState.BOOTED,
            SimulatorState.TRANSITIONING -> {
                val result = CommandExecutor.execute(
                    "xcrun",
                    "simctl",
                    "shutdown",
                    device.udid,
                    timeout = SHUTDOWN_COMMAND_TIMEOUT
                )

                val stateAfterCommand = getSimulatorState(device)

                check(
                    result.isSuccessful ||
                            stateAfterCommand == SimulatorState.SHUTDOWN
                ) {
                    "Failed to shutdown iOS simulator '${device.id}'. " +
                            "Current state: $currentState. " +
                            "Exit code: ${result.exitCode}. " +
                            "Output: ${result.output}"
                }
            }
        }

        val shutdownCompleted = waitUntil(
            timeout = SHUTDOWN_TIMEOUT,
            pollInterval = POLL_INTERVAL
        ) {
            getSimulatorState(device) == SimulatorState.SHUTDOWN
        }

        check(shutdownCompleted) {
            "iOS simulator '${device.id}' did not reach Shutdown state " +
                    "within $SHUTDOWN_TIMEOUT. " +
                    "Current state: ${getSimulatorState(device)}"
        }
    }

    private fun waitUntilBooted(
        device: Device
    ) {
        val result = CommandExecutor.execute(
            "xcrun",
            "simctl",
            "bootstatus",
            device.udid,
            "-b",
            timeout = BOOT_TIMEOUT
        )

        check(
            result.isSuccessful &&
                    getSimulatorState(device) == SimulatorState.BOOTED
        ) {
            "iOS simulator '${device.id}' did not finish booting " +
                    "within $BOOT_TIMEOUT. " +
                    "Exit code: ${result.exitCode}. " +
                    "Output: ${result.output}"
        }
    }

    private fun getSimulatorState(
        device: Device
    ): SimulatorState {
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

        val simulatorLine = result.output
            .lineSequence()
            .map(String::trim)
            .firstOrNull { line ->
                line.contains(device.udid)
            }
            ?: return SimulatorState.ABSENT

        return when {
            simulatorLine.contains(
                "(Booted)",
                ignoreCase = true
            ) -> SimulatorState.BOOTED

            simulatorLine.contains(
                "(Shutdown)",
                ignoreCase = true
            ) -> SimulatorState.SHUTDOWN

            else -> SimulatorState.TRANSITIONING
        }
    }

    private suspend fun waitUntil(
        timeout: Duration,
        pollInterval: Duration,
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

    private enum class SimulatorState {
        ABSENT,
        SHUTDOWN,
        BOOTED,
        TRANSITIONING
    }

    private val BOOT_TIMEOUT = 2.minutes
    private val BOOT_COMMAND_TIMEOUT = 30.seconds

    private val SHUTDOWN_TIMEOUT = 30.seconds
    private val SHUTDOWN_COMMAND_TIMEOUT = 30.seconds

    private val POLL_INTERVAL = 500.milliseconds
}