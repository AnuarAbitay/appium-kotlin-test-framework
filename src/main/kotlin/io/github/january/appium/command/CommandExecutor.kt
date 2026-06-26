package io.github.january.appium.command

import java.io.IOException
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object CommandExecutor {

    private const val COMMAND_START_FAILURE = -1
    private const val COMMAND_TIMEOUT_EXIT_CODE = -2
    private const val COMMAND_INTERRUPTED_EXIT_CODE = -3

    fun execute(
        vararg command: String,
        timeout: Duration = 30.seconds
    ): CommandResult {
        var process: Process? = null

        return try {
            process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            var output = ""

            val outputThread = Thread.startVirtualThread {
                output = process.inputStream
                    .bufferedReader()
                    .use { reader ->
                        reader.readText().trim()
                    }
            }

            val completed = process.waitFor(
                timeout.inWholeMilliseconds,
                MILLISECONDS
            )

            if (!completed) {
                process.destroy()

                if (!process.waitFor(2, SECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(2, SECONDS)
                }

                outputThread.join(2_000)

                return CommandResult(
                    exitCode = COMMAND_TIMEOUT_EXIT_CODE,
                    output = buildString {
                        append("Command timed out after $timeout")

                        if (output.isNotBlank()) {
                            append(". Output: ")
                            append(output)
                        }
                    }
                )
            }

            outputThread.join()

            CommandResult(
                exitCode = process.exitValue(),
                output = output
            )
        } catch (exception: IOException) {
            CommandResult(
                exitCode = COMMAND_START_FAILURE,
                output = exception.message.orEmpty()
            )
        } catch (exception: InterruptedException) {
            process?.destroyForcibly()
            Thread.currentThread().interrupt()

            CommandResult(
                exitCode = COMMAND_INTERRUPTED_EXIT_CODE,
                output = exception.message.orEmpty()
            )
        }
    }

    fun startInBackground(vararg command: String): Process {
        return ProcessBuilder(*command)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
    }
}