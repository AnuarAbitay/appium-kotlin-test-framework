package io.github.january.appium.session

object MobileSessionHolder {

    private val currentSession =
        ThreadLocal<MobileSession>()

    fun set(session: MobileSession) {
        check(currentSession.get() == null) {
            "Mobile session has already been initialized " +
                    "for the current thread"
        }

        currentSession.set(session)
    }

    fun get(): MobileSession {
        return currentSession.get()
            ?: error(
                "Mobile session has not been initialized " +
                        "for the current thread"
            )
    }

    fun getOrNull(): MobileSession? {
        return currentSession.get()
    }

    fun isInitialized(): Boolean {
        return currentSession.get() != null
    }

    fun clear() {
        currentSession.remove()
    }
}