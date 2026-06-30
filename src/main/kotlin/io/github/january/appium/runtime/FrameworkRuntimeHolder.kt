package io.github.january.appium.runtime

object FrameworkRuntimeHolder {

    private val lifecycleLock = Any()

    @Volatile
    private var currentRuntime: FrameworkRuntime? = null

    fun set(runtime: FrameworkRuntime) {
        synchronized(lifecycleLock) {
            check(currentRuntime == null) {
                "FrameworkRuntime has already been initialized"
            }

            check(runtime.isStarted()) {
                "FrameworkRuntime must be started before registration"
            }

            currentRuntime = runtime
        }
    }

    fun get(): FrameworkRuntime {
        return currentRuntime
            ?: error(
                "FrameworkRuntime has not been initialized. " +
                        "Create and start it in @BeforeSuite first"
            )
    }

    fun getOrNull(): FrameworkRuntime? {
        return currentRuntime
    }

    fun isInitialized(): Boolean {
        return currentRuntime != null
    }

    fun clear() {
        synchronized(lifecycleLock) {
            currentRuntime = null
        }
    }
}