package com.lumetv.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Many IPTV providers only accept player-style user agents. */
const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("Server replied ${res.code}")
            res.body?.string() ?: ""
        }
    }

    suspend fun getJson(url: String): JsonElement {
        val text = getText(url)
        if (text.isBlank()) return JsonNull
        return json.parseToJsonElement(text)
    }
}

// ---- Lenient JSON helpers: Xtream servers mix strings, numbers and nulls freely ----

fun JsonElement?.str(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    val c = p.content.trim()
    return if (c.isEmpty() || c == "null") null else c
}

fun JsonObject.s(key: String): String? = this[key].str()
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
fun JsonElement?.asArray(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

fun String.fromBase64(): String = try {
    String(Base64.decode(this, Base64.DEFAULT), Charsets.UTF_8)
} catch (e: Exception) {
    this
}
