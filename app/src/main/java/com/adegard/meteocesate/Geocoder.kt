package com.adegard.meteocesate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/** Open-Meteo free geocoding API - no key, no tracking */
object Geocoder {
    private val client = OkHttpClient()

    suspend fun search(query: String): List<City> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(query, "UTF-8") +
            "&count=10&language=en&format=json"
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            val body = resp.body!!.string()
            val results = JSONObject(body).optJSONArray("results") ?: return@use emptyList()
            (0 until results.length()).mapNotNull { i ->
                val r = results.getJSONObject(i)
                City(
                    name = r.getString("name"),
                    admin1 = r.optString("admin1", ""),
                    country = r.optString("country", ""),
                    lat = r.getDouble("latitude"),
                    lon = r.getDouble("longitude")
                )
            }
        }
    }
}
