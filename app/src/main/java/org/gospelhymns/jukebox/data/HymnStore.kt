package org.gospelhymns.jukebox.data

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

interface HymnalApi {
    fun get(url: String): String
}

class OkHttpHymnalApi(private val client: OkHttpClient) : HymnalApi {
    override fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
            .header("Referer", "https://www.hymnal.net/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("hymnal.net 回應 ${response.code}")
            }
            return response.body?.string() ?: error("空白回應")
        }
    }
}

class HymnCache(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        dir.mkdirs()
    }

    fun read(number: Int): HymnDetail? {
        val file = file(number)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(HymnDetail.serializer(), file.readText()) }.getOrNull()
    }

    fun write(detail: HymnDetail) {
        file(detail.number).writeText(json.encodeToString(HymnDetail.serializer(), detail))
    }

    fun all(): List<HymnDetail> {
        val files = dir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching { json.decodeFromString(HymnDetail.serializer(), file.readText()) }.getOrNull()
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun file(number: Int) = File(dir, "$number.json")
}

fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
