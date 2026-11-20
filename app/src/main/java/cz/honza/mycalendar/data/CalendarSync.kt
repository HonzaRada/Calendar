package cz.honza.mycalendar.data

import cz.honza.mycalendar.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class SyncConnection(val endpoint: String, val token: String) {
    val workspace: String
        get() = MessageDigest.getInstance("SHA-256")
            .digest("$endpoint\n$token".toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        fun parse(endpoint: String, token: String): SyncConnection? {
            val uri = runCatching { URI(endpoint.trim().trimEnd('/')) }.getOrNull() ?: return null
            val host = uri.host ?: return null
            val localDebug = BuildConfig.DEBUG && uri.scheme == "http" && host in setOf("10.0.2.2", "localhost")
            if (uri.scheme != "https" && !localDebug) return null
            if (uri.userInfo != null || uri.query != null || uri.fragment != null || uri.port > 65535) return null
            val secret = token.trim()
            if (!secret.matches(Regex("[A-Za-z0-9_-]{32,128}"))) return null
            return SyncConnection(uri.toASCIIString(), secret)
        }
    }
}

internal enum class SyncStatus { IDLE, SYNCING, SYNCED, UNAVAILABLE, UNAUTHORIZED, INVALID_RESPONSE }
internal class SyncFailure(val status: SyncStatus) : Exception()
internal data class SyncChange(val id: String, val value: JSONObject?, val mutation: String? = null)
internal data class SyncSnapshot(val cursor: Long, val changes: List<SyncChange>)
internal data class SyncResponse(val cursor: Long, val changes: List<SyncChange>, val more: Boolean)

internal fun exchange(settings: SyncConnection, snapshot: SyncSnapshot): SyncResponse {
    val changes = JSONArray()
    for (change in snapshot.changes) {
        changes.put(JSONObject().put("id", change.id).put("value", change.value ?: JSONObject.NULL)
            .put("mutation", change.mutation))
    }
    val bytes = JSONObject().put("cursor", snapshot.cursor).put("changes", changes).toString().toByteArray()
    val connection = URI("${settings.endpoint}/v1/sync").toURL().openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer ${settings.token}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }
        when (connection.responseCode) {
            200 -> Unit
            401, 403 -> throw SyncFailure(SyncStatus.UNAUTHORIZED)
            in 500..599 -> throw SyncFailure(SyncStatus.UNAVAILABLE)
            else -> throw SyncFailure(SyncStatus.INVALID_RESPONSE)
        }
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val response = JSONObject(body)
        val cursor = response.getLong("cursor")
        val received = response.getJSONArray("changes")
        if (cursor < snapshot.cursor || received.length() > 128) throw SyncFailure(SyncStatus.INVALID_RESPONSE)
        val records = List(received.length()) { index ->
            val record = received.getJSONObject(index)
            SyncChange(record.getString("id"), if (record.isNull("value")) null else record.getJSONObject("value"))
        }
        return SyncResponse(cursor, records, response.getBoolean("more"))
    } catch (_: IOException) {
        throw SyncFailure(SyncStatus.UNAVAILABLE)
    } catch (_: JSONException) {
        throw SyncFailure(SyncStatus.INVALID_RESPONSE)
    } finally {
        connection.disconnect()
    }
}
