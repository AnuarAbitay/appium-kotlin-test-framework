package io.github.january.appium.command

data class CommandResult(
    val exitCode: Int,
    val output: String
) {
    val isSuccessful: Boolean
        get() = exitCode == SUCCESS_EXIT_CODE

    val isStartFailure: Boolean
        get() = exitCode == START_FAILURE_EXIT_CODE

    val isTimedOut: Boolean
        get() = exitCode == TIMEOUT_EXIT_CODE

    val isInterrupted: Boolean
        get() = exitCode == INTERRUPTED_EXIT_CODE

    companion object {
        const val SUCCESS_EXIT_CODE = 0
        const val START_FAILURE_EXIT_CODE = -1
        const val TIMEOUT_EXIT_CODE = -2
        const val INTERRUPTED_EXIT_CODE = -3
    }
}