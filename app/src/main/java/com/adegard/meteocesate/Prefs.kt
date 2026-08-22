package com.adegard.meteocesate

import android.content.Context
import org.json.JSONObject

data class City(
    val name: String,
    val admin1: String,
    val country: String,
    val lat: Double,
    val lon: Double
) {
    val display: String
        get() = listOf(name, admin1, country).filter { it.isNotBlank() }.joinToString(", ")

    fun toJson(): String = JSONObject()
        .put("name", name)
        .put("admin1", admin1)
        .put("country", country)
        .put("lat", lat)
        .put("lon", lon)
        .toString()

    companion object {
        fun fromJson(s: String): City? = try {
            val o = JSONObject(s)
            City(o.getString("name"), o.optString("admin1", ""), o.optString("country", ""),
                o.getDouble("lat"), o.getDouble("lon"))
        } catch (_: Exception) {
            null
        }

        val DEFAULT = City("Cesate", "Lombardy", "Italy", 45.6497, 9.1325)
    }
}

object Prefs {
    private const val FILE = "settings"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getCity(ctx: Context): City =
        sp(ctx).getString("city", null)?.let { City.fromJson(it) } ?: City.DEFAULT

    fun saveCity(ctx: Context, c: City) {
        sp(ctx).edit().putString("city", c.toJson()).apply()
    }
}
