package io.github.jsnjack.wallet.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Settings that follow the user to every device through Drive. Like a card,
 * they are one file whose newer copy wins; [updatedAt] is 0 until set here.
 */
@Serializable
data class SyncedSettings(
    @SerialName("v") val version: Int = FORMAT_VERSION,
    val flightApiKey: String = "",
    val updatedAt: Long = 0,
) {
    /** The JSON stored in Drive. */
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private const val FORMAT_VERSION = 1
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Settings from a Drive file, or null when it is unreadable. */
        fun decode(text: String): SyncedSettings? = try {
            json.decodeFromString(serializer(), text)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
