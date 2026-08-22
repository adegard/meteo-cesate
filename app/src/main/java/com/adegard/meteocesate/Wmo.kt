package com.adegard.meteocesate

object Wmo {
    fun label(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45 -> "Fog"
        48 -> "Freezing fog"
        51 -> "Light drizzle"
        53 -> "Drizzle"
        55 -> "Dense drizzle"
        56 -> "Freezing drizzle"
        57 -> "Dense freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66 -> "Freezing rain"
        67 -> "Heavy freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Light showers"
        81 -> "Showers"
        82 -> "Violent showers"
        85 -> "Snow showers"
        86 -> "Heavy snow showers"
        95 -> "Thunderstorm"
        96 -> "Thunderstorm, light hail"
        99 -> "Severe thunderstorm with hail"
        else -> "—"
    }

    fun icon(code: Int): String = when (code) {
        0 -> "☀️"; 1 -> "🌤️"; 2 -> "⛅"; 3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        61, 63, 66, 67 -> "🌧️"
        65 -> "🌧️"
        in 71..77 -> "🌨️"
        80 -> "🌦️"; 81 -> "🌧️"; 82 -> "⛈️"
        85 -> "🌨️"; 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "❓"
    }

    fun isStorm(code: Int) = code in 95..99

    fun isHail(code: Int) = code >= 96

    fun uvText(uv: Double): String = when {
        uv < 3 -> "Low"; uv < 6 -> "Moderate"; uv < 8 -> "High"; uv < 11 -> "Very high"
        else -> "Extreme"
    }

    private val DIRS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    fun dir(deg: Int): String = DIRS[(((deg % 360) + 360) % 360 / 22.5).toInt() % 16]

    /** "today at 15:00" / "tomorrow at 03:00" / "Wednesday at 09:00" */
    fun whenLabel(iso: String): String {
        val cal = java.util.Calendar.getInstance()
        val today = java.util.Calendar.getInstance()
        try {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US)
            cal.time = f.parse(iso)!!
        } catch (_: Exception) {
            return iso.replace('T', ' ')
        }
        val hhmm = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        val dayDiff = ((cal.timeInMillis - today.timeInMillis) / 86400000L).toInt()
        return when {
            dayDiff <= 0 -> "today at $hhmm"
            dayDiff == 1 -> "tomorrow at $hhmm"
            else -> java.text.SimpleDateFormat("EEEE", java.util.Locale.ENGLISH).format(cal.time) + " at $hhmm"
        }
    }
}
