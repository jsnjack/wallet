package io.github.jsnjack.wallet.drive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import io.github.jsnjack.wallet.core.RemoteFile
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** An HTTP error from the Drive API. */
class DriveException(val status: Int, message: String) : IOException(message)

/**
 * Drive REST v3 calls for the card and settings files in the hidden app folder. Blocking:
 * call from an IO thread. Each file carries its "last changed" time in
 * appProperties, so the listing alone decides what to transfer.
 */
class DriveClient(private val token: String) {

    /** Every file in the app folder: card files and the settings file. */
    fun listFiles(): List<RemoteFile> {
        val entries = mutableListOf<RemoteFile>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$API/files?spaces=appDataFolder&pageSize=$PAGE_SIZE&fields=")
                append(encode("nextPageToken,files(id,name,appProperties)"))
                pageToken?.let { append("&pageToken=").append(encode(it)) }
            }
            val page = Json.parseToJsonElement(request("GET", url).decodeToString()).jsonObject
            page["files"]?.jsonArray?.forEach { element ->
                val file = element.jsonObject
                val name = file.string("name") ?: return@forEach
                val fileId = file.string("id") ?: return@forEach
                val updatedAt = file["appProperties"]?.jsonObject?.string(UPDATED_AT)?.toLongOrNull() ?: 0L
                entries += RemoteFile(name, fileId, updatedAt)
            }
            pageToken = page.string("nextPageToken")
        } while (pageToken != null)
        return entries
    }

    /** The content of file [fileId]. */
    fun download(fileId: String): String = request("GET", "$API/files/${encode(fileId)}?alt=media").decodeToString()

    /** Creates the file [name] and returns its file id. */
    fun create(name: String, json: String, updatedAt: Long): String {
        val metadata = buildJsonObject {
            put("name", name)
            putJsonArray("parents") { add(JsonPrimitive("appDataFolder")) }
            putJsonObject("appProperties") { put(UPDATED_AT, updatedAt.toString()) }
        }
        val response = multipart("POST", "$UPLOAD/files?uploadType=multipart&fields=id", metadata, json)
        return Json.parseToJsonElement(response.decodeToString()).jsonObject.string("id")
            ?: throw IOException("Drive create returned no file id")
    }

    /** Replaces the content of file [fileId]. */
    fun update(fileId: String, json: String, updatedAt: Long) {
        val metadata = buildJsonObject {
            putJsonObject("appProperties") { put(UPDATED_AT, updatedAt.toString()) }
        }
        // HttpURLConnection has no PATCH; Google APIs accept the override header.
        multipart("POST", "$UPLOAD/files/${encode(fileId)}?uploadType=multipart&fields=id", metadata, json, override = "PATCH")
    }

    /** The signed-in account's address, shown in Settings. */
    fun accountEmail(): String? {
        val about = Json.parseToJsonElement(request("GET", "$API/about?fields=user(emailAddress)").decodeToString())
        return about.jsonObject["user"]?.jsonObject?.string("emailAddress")
    }

    private fun multipart(method: String, url: String, metadata: JsonObject, json: String, override: String? = null): ByteArray {
        val body = buildString {
            append("--$BOUNDARY\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$BOUNDARY\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json)
            append("\r\n--$BOUNDARY--\r\n")
        }.encodeToByteArray()
        return request(method, url, body, "multipart/related; boundary=$BOUNDARY", override)
    }

    private fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
        override: String? = null,
    ): ByteArray {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Authorization", "Bearer $token")
            override?.let { conn.setRequestProperty("X-HTTP-Method-Override", it) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val status = conn.responseCode
            if (status !in 200..299) {
                val detail = conn.errorStream?.use { it.readBytes().decodeToString().take(ERROR_DETAIL_CHARS) }.orEmpty()
                throw DriveException(status, "${override ?: method} ${url.substringBefore('?')} returned HTTP $status: $detail")
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val UPDATED_AT = "updatedAt"
        const val BOUNDARY = "wallet-card-boundary"
        const val PAGE_SIZE = 1000
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val ERROR_DETAIL_CHARS = 300
    }
}
