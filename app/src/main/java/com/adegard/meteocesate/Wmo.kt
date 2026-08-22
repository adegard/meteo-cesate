package com.adegard.meteocesate

object Wmo {
    fun label(code: Int): String = when (code) {
        0 -> "Sereno"
        1 -> "Prevalentemente sereno"
        2 -> "Poco nuvoloso"
        3 -> "Coperto"
        45 -> "Nebbia"
        48 -> "Nebbia con brina"
        51 -> "Pioviggine debole"
        53 -> "Pioviggine"
        55 -> "Pioviggine intensa"
        56 -> "Pioviggine gelata"
        57 -> "Pioviggine gelata intensa"
        61 -> "Pioggia debole"
        63 -> "Pioggia moderata"
        65 -> "Pioggia forte"
        66 -> "Pioggia gelata"
        67 -> "Pioggia gelata forte"
        71 -> "Neve debole"
        73 -> "Neve moderata"
        75 -> "Neve forte"
        77 -> "Gragnola"
        80 -> "Rovesci deboli"
        81 -> "Rovesci"
        82 -> "Rovesci forti"
        85 -> "Rovesci di neve"
        86 -> "Rovesci di neve forti"
        95 -> "Temporale"
        96 -> "Temporale con grandine"
        99 -> "Temporale forte con grandine"
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
        uv < 3 -> "Basso"; uv < 6 -> "Moderato"; uv < 8 -> "Alto"; uv < 11 -> "Molto alto"
        else -> "Estremo"
    }

    private val DIRS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO"
    )

    fun dir(deg: Int): String = DIRS[(((deg % 360) + 360) % 360 / 22.5).toInt() % 16]

    /** "oggi alle 15:00" / "domani alle 03:00" / "mercoledì alle 09:00" */
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
            dayDiff <= 0 -> "oggi alle $hhmm"
            dayDiff == 1 -> "domani alle $hhmm"
            else -> {
                val name = java.text.SimpleDateFormat("EEEE", java.util.Locale.ITALIAN).format(cal.time)
                "$name alle $hhmm"
            }
        }
    }
}
