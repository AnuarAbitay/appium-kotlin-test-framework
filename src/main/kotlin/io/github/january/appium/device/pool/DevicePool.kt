package io.github.january.appium.device.pool

import io.github.january.appium.device.data.Device
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DevicePool {

    private val availableDevices = LinkedBlockingQueue<Device>()

    private val registeredDevices = ConcurrentHashMap<String, Device>()

    private val acquiredDeviceIds = ConcurrentHashMap.newKeySet<String>()

    private val initialized = AtomicBoolean(false)

    fun initialize(devices: Collection<Device>) {
        require(devices.isNotEmpty()) {
            "DevicePool cannot be initialized with an empty device list"
        }

        check(initialized.compareAndSet(false, true)) {
            "DevicePool has already been initialized"
        }

        try {
            validateUniqueIds(devices)

            devices.forEach { device ->
                registeredDevices[device.id] = device
                availableDevices.add(device)
            }
        } catch (exception: Exception) {
            clear()
            throw exception
        }
    }

    fun acquire(timeout: Duration = Duration.ofMinutes(5)): Device {
        check(initialized.get()) {
            "DevicePool has not been initialized"
        }

        require(!timeout.isNegative) {
            "Device acquire timeout must not be negative"
        }

        val device = availableDevices.poll(
            timeout.toMillis(),
            TimeUnit.MILLISECONDS
        ) ?: error(
            "No available device after waiting for $timeout"
        )

        check(acquiredDeviceIds.add(device.id)) {
            "Device '${device.id}' has already been acquired"
        }

        return device
    }

    fun release(device: Device) {
        check(initialized.get()) {
            "DevicePool has not been initialized"
        }

        val registeredDevice = registeredDevices[device.id]
            ?: error(
                "Device '${device.id}' is not registered in DevicePool"
            )

        check(registeredDevice == device) {
            "Device '${device.id}' does not match the registered configuration"
        }

        check(acquiredDeviceIds.remove(device.id)) {
            "Device '${device.id}' was not acquired or has already been released"
        }

        check(availableDevices.offer(device)) {
            "Failed to return device '${device.id}' to DevicePool"
        }
    }

    fun availableCount(): Int =
        availableDevices.size

    fun acquiredCount(): Int =
        acquiredDeviceIds.size

    fun registeredCount(): Int =
        registeredDevices.size

    fun isInitialized(): Boolean =
        initialized.get()

    fun clear() {
        availableDevices.clear()
        registeredDevices.clear()
        acquiredDeviceIds.clear()
        initialized.set(false)
    }

    private fun validateUniqueIds(devices: Collection<Device>) {
        val duplicateIds = devices
            .groupingBy { it.id }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

        require(duplicateIds.isEmpty()) {
            "Duplicate device ids: ${duplicateIds.joinToString()}"
        }
    }
}