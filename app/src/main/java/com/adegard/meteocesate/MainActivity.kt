package com.adegard.meteocesate

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var tvTitle: TextView
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
                toast(getString(R.string.alerts_enabled))
            } else {
                toast(getString(R.string.permission_denied))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipe = findViewById(R.id.swipe)
        tvTitle = findViewById(R.id.tvTitle)
        tvUpd = findViewById(R.id.tvUpd)
        tvAlert = findViewById(R.id.tvAlert)
        tvIcon = findViewById(R.id.tvIcon)
        tvTemp = findViewById(R.id.tvTemp)
        tvCond = findViewById(R.id.tvCond)
        gridNow = findViewById(R.id.gridNow)
        llHours = findViewById(R.id.llHours)
        llDays = findViewById(R.id.llDays)
        btnBell = findViewById(R.id.btnBell)

        findViewById<Button>(R.id.btnSearch).setOnClickListener { showCityPicker() }
        btnBell.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                StormWorker.schedule(this)
                toast(getString(R.string.alerts_already))
            }
        }
        swipe.setOnRefreshListener { load() }
        updateBell()
        load()
        StormWorker.schedule(this)
    }

    /* ---------- city picker ---------- */

    private fun showCityPicker() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(16))
        }
        val edit = EditText(this).apply {
            hint = getString(R.string.search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val list = ListView(this)
        box.addView(edit)
        box.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(320)))

        val items = mutableListOf<City>()
        val labels = mutableListOf<String>()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        list.adapter = adapter

        var job: Job? = null
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val q = s?.toString()?.trim().orEmpty()
                job?.cancel()
                if (q.length < 2) return
                job = lifecycleScope.launch {
                    delay(350)
                    try {
                        val found = Geocoder.search(q)
                        items.clear(); items.addAll(found)
                        labels.clear(); labels.addAll(found.map { it.display })
                        if (found.isEmpty()) labels.add(getString(R.string.no_results))
                        adapter.notifyDataSetChanged()
                    } catch (_: Exception) {}
                }
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        var dlg: AlertDialog? = null
        list.setOnItemClickListener { _, _, pos, _ ->
            val city = items.getOrNull(pos) ?: return@setOnItemClickListener
            Prefs.saveCity(this, city)
            StormWorker.schedule(this)
            load()
            toast(getString(R.string.city_set, city.name))
            dlg?.dismiss()
        }

        dlg = AlertDialog.Builder(this)
            .setTitle(R.string.pick_city)
            .setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dlg.show()
    }

    /* ---------- data ---------- */

    private fun updateBell() {
        val on = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        btnBell.text = getString(if (on) R.string.alerts_on else R.string.alerts_off)
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val city = Prefs.getCity(this@MainActivity)
                val w = WeatherRepo.fetch(city)
                render(w)
                showAlert(w)
                tvTitle.text = city.name
                val loc = listOf(city.admin1, city.country).filter { it.isNotBlank() }.joinToString(", ")
                tvUpd.text = buildString {
                    if (loc.isNotEmpty()) append("$loc · ")
                    append("updated ")
                    append(java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date()))
                }
            } catch (e: Exception) {
                tvUpd.text = "network error: ${e.message}"
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
                append("⚠️ Thunderstorms coming — expected ")
                append(Wmo.whenLabel(s.iso))
                if (Wmo.isHail(s.code)) append(" (possible hail)")
            }
        }
    }

    /* ---------- rendering ---------- */

    private fun render(w: Weather) {
        tvIcon.text = Wmo.icon(w.curCode)
        tvTemp.text = "${Math.round(w.curTemp)}°"
        tvCond.text = Wmo.label(w.curCode)

        gridNow.removeAllViews()
        val d0 = w.days.firstOrNull()
        val cells = mutableListOf(
            "Feels like" to "${Math.round(w.curApp)}°",
            "Humidity" to "${w.curHum}%",
            "Wind" to "${Math.round(w.curWind)} km/h ${Wmo.dir(w.curDir)}",
            "Pressure" to "${Math.round(w.curPres)} mb",
            "Rain 1h" to if (w.curPrec > 0) "${w.curPrec} mm" else "none"
        )
        if (d0 != null) {
            cells += "UV max" to String.format(java.util.Locale.US, "%.1f %s", d0.uvMax, Wmo.uvText(d0.uvMax))
            cells += "Sunrise" to d0.sunrise
            cells += "Sunset" to d0.sunset
            cells += "Rain chance" to "${d0.popMax}%"
        }
        var col = 0
        while (col < cells.size) {
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(3) { k ->
                val idx2 = col + k
                if (idx2 < cells.size) rowLayout.addView(makeCell(cells[idx2].first, cells[idx2].second), cellParams())
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
            addText(colView, if (idx == 0) "now" else h.hhmm, 10, R.color.muted, false)
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
