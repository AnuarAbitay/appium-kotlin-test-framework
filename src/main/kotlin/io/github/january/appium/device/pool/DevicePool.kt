package io.github.january.appium.device.pool

import io.github.january.appium.device.data.Device
import io.github.january.appium.device.data.DeviceConfig
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object DevicePool {

    private val devices = LinkedBlockingQueue<Device>()

    fun initialize(config: DeviceConfig) {
        check(devices.isEmpty()) {
            "DevicePool has already been initialized"
        }

        check(config.devices.isNotEmpty()) {
            "No devices found in devices.yml"
        }

        devices.addAll(config.devices)
    }

    fun acquire(timeoutSeconds: Long = 60): Device {
        return devices.poll(timeoutSeconds, TimeUnit.SECONDS)
            ?: error(
                "No available device after waiting $timeoutSeconds seconds"
            )
    }

    fun release(device: Device) {
        devices.offer(device)
    }

    fun availableCount(): Int = devices.size

    fun clear() {
        devices.clear()
    }
}