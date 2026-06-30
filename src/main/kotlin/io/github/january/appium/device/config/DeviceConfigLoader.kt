package io.github.january.appium.device.config

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.january.appium.device.data.DeviceConfig
import java.io.IOException

object DeviceConfigLoader {

    private const val DEFAULT_RESOURCE_NAME = "devices.yml"

    private val objectMapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule.Builder().build())
        .configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            true
        )
        .configure(
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
            true
        )

    fun load(
        resourceName: String = DEFAULT_RESOURCE_NAME
    ): DeviceConfig {
        require(resourceName.isNotBlank()) {
            "Device configuration resource name must not be blank"
        }

        val normalizedResourceName = resourceName.removePrefix("/")

        val classLoader =
            Thread.currentThread().contextClassLoader
                ?: DeviceConfigLoader::class.java.classLoader

        val inputStream = classLoader
            .getResourceAsStream(normalizedResourceName)
            ?: throw IllegalStateException(
                "Device configuration '$normalizedResourceName' " +
                        "was not found in classpath"
            )

        return try {
            inputStream.use { stream ->
                objectMapper.readValue<DeviceConfig>(stream)
            }
        } catch (exception: JacksonException) {
            throw IllegalArgumentException(
                "Failed to parse device configuration " +
                        "'$normalizedResourceName': " +
                        exception.originalMessage,
                exception
            )
        } catch (exception: IOException) {
            throw IllegalStateException(
                "Failed to read device configuration " +
                        "'$normalizedResourceName'",
                exception
            )
        }
    }
}