package dev.opencode.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatEntry(val role: String, val text: String)

/** Small client for OpenCode's documented local HTTP server API (no bundled WebView UI). */
class OpenCodeApi(private val base: String = "http://127.0.0.1:4096") {
    suspend fun health(): Boolean = request("GET", "/global/health")?.optBoolean("healthy", false) == true

    suspend fun newSession(title: String = "Mobile coding session"): String? =
        request("POST", "/session", JSONObject().put("title", title))?.optString("id")?.takeIf { it.isNotBlank() }

    suspend fun messages(sessionId: String): List<ChatEntry> {
        val conn = connection("GET", "/session/${segment(sessionId)}/message?limit=60") ?: return emptyList()
        return try {
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val role = item.optJSONObject("info")?.optString("role") ?: continue
                    val parts = item.optJSONArray("parts") ?: continue
                    val text = buildString {
                        for (j in 0 until parts.length()) {
                            val part = parts.optJSONObject(j) ?: continue
                            if (part.optString("type") == "text") append(part.optString("text"))
                        }
                    }.trim()
                    if (text.isNotBlank()) add(ChatEntry(role, text))
                }
            }
        } finally { conn.disconnect() }
    }

    suspend fun send(sessionId: String, text: String): List<ChatEntry> {
        val body = JSONObject().put("parts", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
        request("POST", "/session/${segment(sessionId)}/message", body)
        return messages(sessionId)
    }

    suspend fun mcpStatus(): JSONObject? = request("GET", "/mcp")

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject? = withContext(Dispatchers.IO) {
        val conn = connection(method, path, body) ?: return@withContext null
        try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (_: Exception) { null } finally { conn.disconnect() }
    }

    private fun connection(method: String, path: String, body: JSONObject? = null): HttpURLConnection? = try {
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 1800
            readTimeout = 90_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter().use { it.write(body.toString()) }
            }
        }
    } catch (_: Exception) { null }

    private fun segment(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
