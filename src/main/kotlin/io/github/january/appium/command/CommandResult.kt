package io.github.january.appium.command

data class CommandResult(
    val exitCode: Int,
    val output: String
) {
    val isSuccessful: Boolean
        get() = exitCode == 0

    val isTimedOut: Boolean
        get() = exitCode == -2
}
