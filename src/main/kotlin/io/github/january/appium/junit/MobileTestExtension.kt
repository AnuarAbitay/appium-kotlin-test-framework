package io.github.january.appium.junit

import io.github.january.appium.runtime.FrameworkRuntimeHolder
import io.github.january.appium.session.MobileSession
import io.github.january.appium.session.MobileSessionHolder
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class MobileTestExtension :
    BeforeEachCallback,
    AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        check(!MobileSessionHolder.isInitialized()) {
            "Mobile session was not cleared before test " +
                    "'${context.displayName}'"
        }

        val runtime = FrameworkRuntimeHolder.get()

        check(runtime.isStarted()) {
            "FrameworkRuntime must be started before running mobile tests"
        }

        val device = runtime.devicePool.acquire()

        try {
            val driver = runtime.driverManager
                .createDriverForDevice(device)

            val session = MobileSession(
                device = device,
                driver = driver
            )

            MobileSessionHolder.set(session)

            context.getStore(NAMESPACE)
                .put(SESSION_KEY, session)
        } catch (exception: Exception) {
            MobileSessionHolder.clear()

            runCatching {
                runtime.driverManager
                    .quitDriverForDevice(device)
            }.exceptionOrNull()
                ?.let(exception::addSuppressed)

            runCatching {
                runtime.devicePool.release(device)
            }.exceptionOrNull()
                ?.let(exception::addSuppressed)

            throw IllegalStateException(
                "Failed to initialize mobile session " +
                        "for test '${context.displayName}' " +
                        "on device '${device.id}'",
                exception
            )
        }
    }

    override fun afterEach(context: ExtensionContext) {
        val runtime = FrameworkRuntimeHolder.get()

        val session = context.getStore(NAMESPACE)
            .remove(
                SESSION_KEY,
                MobileSession::class.java
            )

        if (session == null) {
            MobileSessionHolder.clear()
            return
        }

        val failures = mutableListOf<Throwable>()

        try {
            runtime.driverManager
                .quitDriverForDevice(session.device)
        } catch (exception: Exception) {
            failures += IllegalStateException(
                "Failed to quit driver for device " +
                        "'${session.device.id}'",
                exception
            )
        }

        MobileSessionHolder.clear()

        try {
            runtime.devicePool.release(session.device)
        } catch (exception: Exception) {
            failures += IllegalStateException(
                "Failed to release device " +
                        "'${session.device.id}'",
                exception
            )
        }

        if (failures.isNotEmpty()) {
            val cleanupException = IllegalStateException(
                "Mobile session cleanup failed after test " +
                        "'${context.displayName}'"
            )

            failures.forEach(cleanupException::addSuppressed)

            throw cleanupException
        }
    }

    private companion object {

        val NAMESPACE: ExtensionContext.Namespace =
            ExtensionContext.Namespace.create(
                MobileTestExtension::class.java
            )

        const val SESSION_KEY = "mobile-session"
    }
}