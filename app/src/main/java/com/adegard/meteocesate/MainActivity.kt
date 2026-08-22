package com.adegard.meteocesate

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var tvUpd: TextView
    private lateinit var tvAlert: TextView
    private lateinit var tvIcon: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvCond: TextView
    private lateinit var gridNow: LinearLayout
    private lateinit var llHours: LinearLayout
    private lateinit var llDays: LinearLayout
    private lateinit var btnBell: Button

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            updateBell()
            if (granted) {
                StormWorker.schedule(this)
                toast("Avvisi temporali attivi")
            } else {
                toast("Permesso negato: abilitalo dalle impostazioni per gli avvisi")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipe = findViewById(R.id.swipe)
        tvUpd = findViewById(R.id.tvUpd)
        tvAlert = findViewById(R.id.tvAlert)
        tvIcon = findViewById(R.id.tvIcon)
        tvTemp = findViewById(R.id.tvTemp)
        tvCond = findViewById(R.id.tvCond)
        gridNow = findViewById(R.id.gridNow)
        llHours = findViewById(R.id.llHours)
        llDays = findViewById(R.id.llDays)
        btnBell = findViewById(R.id.btnBell)

        btnBell.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                StormWorker.schedule(this)
                toast("Avvisi temporali già attivi")
            }
        }
        swipe.setOnRefreshListener { load() }
        updateBell()
        load()
        StormWorker.schedule(this)
    }

    private fun updateBell() {
        val on = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        btnBell.text = getString(if (on) R.string.alerts_on else R.string.alerts_off)
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val w = WeatherRepo.fetch()
                render(w)
                showAlert(w)
                tvUpd.text = "aggiornato " + java.text.SimpleDateFormat(
                    "HH:mm", java.util.Locale.ITALIAN
                ).format(java.util.Date())
            } catch (e: Exception) {
                tvUpd.text = "errore di rete: ${e.message}"
            } finally {
                swipe.isRefreshing = false
            }
        }
    }

    private fun showAlert(w: Weather) {
        val s = WeatherRepo.firstStorm(w)
        if (s == null) {
            tvAlert.visibility = View.GONE
        } else {
            tvAlert.visibility = View.VISIBLE
            tvAlert.text = buildString {
                append("⚠️ Temporali in arrivo — previsti ")
                append(Wmo.whenLabel(s.iso))
                if (Wmo.isHail(s.code)) append(" (possibile grandine)")
            }
        }
    }

    private fun render(w: Weather) {
        tvIcon.text = Wmo.icon(w.curCode)
        tvTemp.text = "${Math.round(w.curTemp)}°"
        tvCond.text = Wmo.label(w.curCode)

        gridNow.removeAllViews()
        val d0 = w.days.firstOrNull()
        val cells = mutableListOf(
            "Percepita" to "${Math.round(w.curApp)}°",
            "Umidità" to "${w.curHum}%",
            "Vento" to "${Math.round(w.curWind)} km/h ${Wmo.dir(w.curDir)}",
            "Pressione" to "${Math.round(w.curPres)} mb",
            "Pioggia 1h" to if (w.curPrec > 0) "${w.curPrec} mm" else "assenti"
        )
        if (d0 != null) {
            cells += "UV max" to String.format(java.util.Locale.US, "%.1f %s", d0.uvMax, Wmo.uvText(d0.uvMax))
            cells += "Alba" to d0.sunrise
            cells += "Tramonto" to d0.sunset
            cells += "Prob. oggi" to "${d0.popMax}%"
        }
        var col = 0
        while (col < cells.size) {
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(3) { k ->
                val ctxIdx = col + k
                if (ctxIdx < cells.size) rowLayout.addView(makeCell(cells[ctxIdx].first, cells[ctxIdx].second), cellParams())
                else rowLayout.addView(View(this), cellParams())
            }
            gridNow.addView(
                rowLayout,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            )
            col += 3
        }

        llHours.removeAllViews()
        for ((idx, h) in w.hours.withIndex()) {
            val colView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = ContextCompat.getDrawable(context, R.drawable.bg_cell)
                setPadding(dp(6), dp(8), dp(6), dp(8))
            }
            addText(colView, if (idx == 0) "ora" else h.hhmm, 10, R.color.muted, false)
            addText(colView, Wmo.icon(h.code), 20, R.color.txt, false)
            addText(colView, "${Math.round(h.temp)}°", 14, R.color.txt, true)
            addText(colView, h.pop?.let { "$it%" } ?: "", 9, R.color.accent, false)
            val hlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            hlp.setMargins(dp(3), 0, dp(3), 0)
            llHours.addView(colView, hlp)
        }

        llDays.removeAllViews()
        val gMin = w.days.minOf { it.tMin }
        val gMax = w.days.maxOf { it.tMax }
        val span = (gMax - gMin).coerceAtLeast(1.0)
        val barMax = dp(80)
        for ((idx, d) in w.days.withIndex()) {
            if (idx >= 7) break
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(7), 0, dp(7))
            }
            addText(row, d.label, 13, R.color.txt, false, widthDp = 96)
            addText(row, Wmo.icon(d.code), 18, R.color.txt, false, widthDp = 34, center = true)

            val frame = FrameLayout(this)
            val barW = (((d.tMax - d.tMin) / span) * barMax).toInt().coerceAtLeast(dp(6)).coerceAtMost(barMax)
            val off = (((d.tMin - gMin) / span) * barMax).toInt().coerceIn(0, barMax - barW)
            val bar = View(this).apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_bar)
            }
            frame.addView(
                bar,
                FrameLayout.LayoutParams(barW, dp(5), Gravity.CENTER_VERTICAL).apply { leftMargin = off }
            )
            row.addView(frame, LinearLayout.LayoutParams(0, dp(16), 1f))

            addText(row, "${Math.round(d.tMax)}°", 13, R.color.txt, true, widthDp = 30, center = true)
            addText(row, "${Math.round(d.tMin)}°", 13, R.color.muted, false, widthDp = 32, center = true)
            addText(row, if (d.popMax > 0) "${d.popMax}%" else " ", 11, R.color.accent, false, widthDp = 34, center = true)
            llDays.addView(row)
        }
    }

    private fun makeCell(label: String, value: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_cell)
            setPadding(dp(6), dp(8), dp(6), dp(8))
            addText(this, label, 10, R.color.muted, false)
            addText(this, value, 12, R.color.txt, true)
        }

    private fun cellParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        p.setMargins(dp(2), dp(2), dp(2), dp(2))
        return p
    }

    private fun addText(
        parent: LinearLayout, text: String, sizeSp: Int, colorRes: Int,
        bold: Boolean, widthDp: Int = 0, center: Boolean = false
    ) {
        val t = TextView(this)
        t.text = text
        t.textSize = sizeSp.toFloat()
        t.setTextColor(ContextCompat.getColor(this, colorRes))
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        if (center) t.gravity = Gravity.CENTER
        val lp = if (widthDp > 0)
            LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT)
        else
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER_HORIZONTAL
        parent.addView(t, lp)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
