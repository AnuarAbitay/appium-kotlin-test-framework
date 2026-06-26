package io.github.january.appium.device.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.january.appium.device.data.DeviceConfig

object DeviceConfigLoader {
    private val objectMapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule.Builder().build())

    fun load(resourceName: String = "devices.yml"): DeviceConfig {
        val inputStream = Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream(resourceName)
            ?: error(
                "Resource '$resourceName' was not found in src/main/resources"
            )

        return inputStream.use { stream ->
            objectMapper.readValue<DeviceConfig>(stream)
        }
    }
}