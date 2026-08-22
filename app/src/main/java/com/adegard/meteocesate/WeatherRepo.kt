package com.adegard.meteocesate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class Hour(
    val iso: String,
    val hhmm: String,
    val temp: Double,
    val pop: Int?,
    val code: Int
)

data class Day(
    val date: String,
    val label: String,
    val code: Int,
    val tMax: Double,
    val tMin: Double,
    val popMax: Int,
    val uvMax: Double,
    val sunrise: String,
    val sunset: String
)

data class Weather(
    val curTemp: Double,
    val curApp: Double,
    val curHum: Int,
    val curWind: Double,
    val curDir: Int,
    val curPres: Double,
    val curPrec: Double,
    val curCode: Int,
    val hours: List<Hour>,
    val days: List<Day>
)

object WeatherRepo {

    private const val URL =
        "https://api.open-meteo.com/v1/forecast?latitude=45.6497&longitude=9.1325" +
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation," +
        "weather_code,wind_speed_10m,wind_direction_10m,surface_pressure" +
        "&hourly=temperature_2m,precipitation_probability,weather_code" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min," +
        "precipitation_probability_max,uv_index_max,sunrise,sunset" +
        "&timezone=Europe%2FBerlin&forecast_days=8"

    private val client = OkHttpClient()

    suspend fun fetch(): Weather = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(URL).build()
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            parse(resp.body!!.string())
        }
    }

    private fun parse(json: String): Weather {
        val root = JSONObject(json)
        val c = root.getJSONObject("current")
        val h = root.getJSONObject("hourly")
        val d = root.getJSONObject("daily")

        val nowIso = c.getString("time").substring(0, 13)
        val times = h.getJSONArray("time")
        var start = 0
        for (i in 0 until times.length()) {
            if (times.getString(i).substring(0, 13) >= nowIso) { start = i; break }
        }

        val temps = h.getJSONArray("temperature_2m")
        val pops = h.optJSONArray("precipitation_probability")
        val codes = h.getJSONArray("weather_code")

        val hours = ArrayList<Hour>(24)
        var i = start
        while (i < times.length() && hours.size < 24) {
            hours += Hour(
                iso = times.getString(i),
                hhmm = times.getString(i).substring(11, 16),
                temp = temps.getDouble(i),
                pop = pops?.opt(i)?.let { if (it == JSONObject.NULL) null else (it as Number).toInt() },
                code = codes.getInt(i)
            )
            i++
        }

        val days = ArrayList<Day>(7)
        val dayDates = d.getJSONArray("time")
        val dayCodes = d.getJSONArray("weather_code")
        val tMaxA = d.getJSONArray("temperature_2m_max")
        val tMinA = d.getJSONArray("temperature_2m_min")
        val popMaxA = d.getJSONArray("precipitation_probability_max")
        val uvMaxA = d.getJSONArray("uv_index_max")
        val riseA = d.getJSONArray("sunrise")
        val setA = d.getJSONArray("sunset")
        val df = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.ITALIAN)
        for (j in 0 until 7) {
            val cal = java.util.Calendar.getInstance()
            try {
                cal.time = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dayDates.getString(j))!!
            } catch (_: Exception) {}
            days += Day(
                date = dayDates.getString(j),
                label = if (j == 0) "Oggi" else capitalize(df.format(cal.time)),
                code = dayCodes.getInt(j),
                tMax = tMaxA.getDouble(j),
                tMin = tMinA.getDouble(j),
                popMax = popMaxA.opt(j)?.let { if (it == JSONObject.NULL) 0 else (it as Number).toInt() } ?: 0,
                uvMax = uvMaxA.optDouble(j, 0.0),
                sunrise = riseA.getString(j).substring(11, 16),
                sunset = setA.getString(j).substring(11, 16)
            )
        }

        return Weather(
            curTemp = c.getDouble("temperature_2m"),
            curApp = c.getDouble("apparent_temperature"),
            curHum = c.getInt("relative_humidity_2m"),
            curWind = c.getDouble("wind_speed_10m"),
            curDir = c.getInt("wind_direction_10m"),
            curPres = c.getDouble("surface_pressure"),
            curPrec = c.getDouble("precipitation"),
            curCode = c.getInt("weather_code"),
            hours = hours,
            days = days
        )
    }

    private fun capitalize(s: String) =
        s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ITALIAN) else it.toString() }

    fun firstStorm(w: Weather, withinHours: Int = 12): Hour? =
        w.hours.take(withinHours).firstOrNull { Wmo.isStorm(it.code) }
}
