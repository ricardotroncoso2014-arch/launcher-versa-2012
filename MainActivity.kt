package com.versa.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : Activity(), LocationListener {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvOil: TextView
    private lateinit var tvTrip: TextView
    private lateinit var tvBig: TextView
    private lateinit var lines: SpeedLines
    private var maxKmh = 0f
    private var lastFix = 0L
    private var last: Location? = null
    private var tripKm = 0f
    private var oilKm = 5000f
    private var n = 0
    private val cyan = Color.parseColor("#33C6FF")
    private val M = ViewGroup.LayoutParams.MATCH_PARENT
    private val W = ViewGroup.LayoutParams.WRAP_CONTENT
    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            if (System.currentTimeMillis() - lastFix > 4000) { tvBig.text = "0"; lines.setSpeed(0f) }
            h.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("versa", MODE_PRIVATE)
        tripKm = prefs.getFloat("trip", 0f)
        oilKm = prefs.getFloat("oil", 5000f)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildUi())
        refresh()
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1) else startGps()
    }

    override fun onResume() {
        super.onResume()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        h.post(ticker)
    }

    override fun onPause() { super.onPause(); h.removeCallbacks(ticker); save() }

    override fun onDestroy() {
        super.onDestroy()
        try { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this) } catch (e: Exception) {}
    }

    override fun onBackPressed() {} // launcher: no se cierra

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        startGps()
    }

    private fun startGps() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        } catch (e: Exception) {}
    }

    override fun onLocationChanged(loc: Location) {
        var kmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        if (kmh < 2f) kmh = 0f
        lastFix = System.currentTimeMillis()
        tvBig.text = kmh.roundToInt().toString()
        tvBig.setTextColor(when {
            kmh >= 100f -> Color.parseColor("#FF5D5D")
            kmh >= 60f -> Color.parseColor("#FFD166")
            else -> Color.WHITE
        })
        lines.setSpeed(kmh)
        if (kmh > maxKmh) maxKmh = kmh
        tvSpeed.text = "Máx: ${maxKmh.roundToInt()} km/h"
        val p = last
        if (p != null && kmh > 3f) {
            val d = p.distanceTo(loc) / 1000f
            tripKm += d
            oilKm = max(0f, oilKm - d)
            refresh()
        }
        last = loc
        if (++n % 15 == 0) save()
    }
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun save() { prefs.edit().putFloat("trip", tripKm).putFloat("oil", oilKm).apply() }

    private fun refresh() {
        tvOil.text = "En: ${oilKm.toInt()} km"
        tvTrip.text = String.format(Locale.US, "%.1f km", tripKm)
    }

    private fun updateClock() {
        val d = Date()
        tvClock.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(d)
        tvDate.text = SimpleDateFormat("EEE, d MMM yyyy", Locale("es")).format(d)
    }

    private fun rounded(color: String, r: Int, stroke: Boolean): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.parseColor(color))
        g.cornerRadius = dp(r).toFloat()
        if (stroke) g.setStroke(dp(2), cyan)
        return g
    }

    private fun label(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text; t.textSize = size; t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun buildUi(): View {
        val root = FrameLayout(this)
        val bg = ImageView(this)
        bg.setImageResource(R.drawable.bg)
        bg.scaleType = ImageView.ScaleType.CENTER_CROP
        root.addView(bg, FrameLayout.LayoutParams(M, M))
        lines = SpeedLines(this)
        root.addView(lines, FrameLayout.LayoutParams(M, M))

        // Barra superior
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(14), dp(8), dp(14), dp(24))
        top.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#F0050A14"), Color.parseColor("#00050A14")))
        val home = label("⌂", 30f, Color.WHITE, true)
        home.gravity = Gravity.CENTER
        home.background = rounded("#CC0A1626", 14, true)
        home.setOnClickListener { openMenu() }
        top.addView(home, LinearLayout.LayoutParams(dp(64), dp(52)))
        val brand = label("NISSAN  VERSA", 24f, Color.WHITE, true)
        brand.setPadding(dp(16), 0, 0, 0)
        top.addView(brand)
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        tvClock = label("--:--", 32f, Color.WHITE, true)
        tvDate = label("", 15f, Color.parseColor("#C8D6EE"))
        val welcome = label("Bienvenido Nissan Versa", 15f, cyan)
        welcome.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        top.addView(tvClock)
        val dcol = LinearLayout(this)
        dcol.orientation = LinearLayout.VERTICAL
        dcol.setPadding(dp(16), 0, 0, 0)
        dcol.addView(tvDate); dcol.addView(welcome)
        top.addView(dcol)
        root.addView(top, FrameLayout.LayoutParams(M, W, Gravity.TOP))

        // Tarjetas derecha
        tvSpeed = TextView(this); tvSpeed.text = "Máx: 0 km/h"
        tvOil = TextView(this); tvTrip = TextView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val c1 = card("Velocidad máxima del viaje", tvSpeed)
        val c2 = card("Cambio de aceite", tvOil)
        c2.setOnClickListener { editOil() }
        val c3 = card("Recorrido (mantén para reiniciar)", tvTrip)
        c3.setOnLongClickListener { tripKm = 0f; save(); refresh(); true }
        col.addView(c1); col.addView(c2); col.addView(c3)
        val clp = FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.27f).toInt(), W,
            Gravity.END or Gravity.CENTER_VERTICAL)
        clp.setMargins(0, dp(30), dp(14), dp(70))
        root.addView(col, clp)

        // Velocímetro grande
        tvBig = label("0", 84f, Color.WHITE, true)
        tvBig.gravity = Gravity.CENTER
        tvBig.minWidth = dp(190)
        tvBig.setShadowLayer(18f, 0f, 0f, cyan)
        val unit = label("km/h", 18f, Color.parseColor("#C8D6EE"))
        unit.gravity = Gravity.CENTER
        val badge = LinearLayout(this)
        badge.orientation = LinearLayout.VERTICAL
        badge.gravity = Gravity.CENTER_HORIZONTAL
        badge.setPadding(dp(18), dp(6), dp(18), dp(8))
        badge.background = rounded("#99050A14", 24, true)
        badge.addView(tvBig); badge.addView(unit)
        val blp = FrameLayout.LayoutParams(W, W, Gravity.BOTTOM or Gravity.START)
        blp.setMargins(dp(24), 0, 0, dp(110))
        root.addView(badge, blp)

        // Dock
        val dock = LinearLayout(this)
        dock.gravity = Gravity.CENTER
        dock.setPadding(dp(10), dp(8), dp(10), dp(8))
        dock.background = rounded("#CC0A1626", 22, true)
        dockItem(dock, "Menú", emptyList(), "▦", "#1E6BFF", { openMenu() }, null)
        dockItem(dock, "Maps", listOf("com.google.android.apps.maps"), "📍", "#2E7D32",
            { launch(listOf("com.google.android.apps.maps"), false) },
            { launch(listOf("com.google.android.apps.maps"), true) })
        dockItem(dock, "Audio", emptyList(), "♪", "#7B1FA2", {
            try { startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast("No hay app de música") }
        }, null)
        dockItem(dock, "Video", listOf("com.google.android.youtube"), "▶", "#C62828",
            { launch(listOf("com.google.android.youtube"), false) }, null)
        val radio = listOf("com.syu.radio", "com.microntek.radio", "com.android.fmradio", "com.mediatek.fmradio", "com.sprd.fm")
        dockItem(dock, "Radio", radio, "📻", "#0277BD", { launch(radio, false) }, null)
        dockItem(dock, "Waze", listOf("com.waze"), "☺", "#00A8E8",
            { launch(listOf("com.waze"), false) }, { launch(listOf("com.waze"), true) })
        dockItem(dock, "Config", emptyList(), "⚙", "#455A64", {
            try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
        }, null)
        val dlp = FrameLayout.LayoutParams(W, W, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        dlp.bottomMargin = dp(10)
        root.addView(dock, dlp)
        return root
    }

    private fun card(title: String, value: TextView): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(10), dp(14), dp(10))
        c.background = rounded("#CC0A1626", 16, true)
        value.setTextColor(Color.WHITE); value.textSize = 24f; value.typeface = Typeface.DEFAULT_BOLD
        c.addView(label(title, 12f, Color.parseColor("#9FB4D3")))
        c.addView(value)
        val lp = LinearLayout.LayoutParams(M, W)
        lp.bottomMargin = dp(10)
        c.layoutParams = lp
        return c
    }

    private fun iconFor(pkgs: List<String>, glyph: String, color: String): View {
        for (p in pkgs) {
            try {
                val iv = ImageView(this)
                iv.setImageDrawable(packageManager.getApplicationIcon(p))
                iv.layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
                return iv
            } catch (e: Exception) {}
        }
        val tv = label(glyph, 26f, Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.background = rounded(color, 14, false)
        tv.layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
        return tv
    }

    private fun dockItem(parent: LinearLayout, name: String, pkgs: List<String>, glyph: String, color: String,
                         click: () -> Unit, long: (() -> Unit)?) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER_HORIZONTAL
        item.setPadding(dp(14), dp(2), dp(14), dp(2))
        item.addView(iconFor(pkgs, glyph, color))
        val t = label(name, 13f)
        t.gravity = Gravity.CENTER
        item.addView(t)
        item.setOnClickListener { click() }
        if (long != null) item.setOnLongClickListener { long(); true }
        parent.addView(item)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun launch(pkgs: List<String>, adjacent: Boolean) {
        for (p in pkgs) {
            val i = packageManager.getLaunchIntentForPackage(p) ?: continue
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (adjacent) i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            try { startActivity(i) } catch (e: Exception) { toast("No se pudo abrir") }
            return
        }
        toast("App no instalada")
    }

    private fun editOil() {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.setText(oilKm.toInt().toString())
        AlertDialog.Builder(this).setTitle("Km para el próximo cambio de aceite").setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                et.text.toString().toFloatOrNull()?.let { oilKm = it; save(); refresh() }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun openMenu() {
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(Color.parseColor("#0B0F17"))
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), dp(8), dp(16), dp(8))
        head.addView(label("Aplicaciones", 22f, Color.WHITE, true), LinearLayout.LayoutParams(0, W, 1f))
        val x = label("✕", 28f, Color.WHITE, true)
        x.setPadding(dp(16), dp(4), dp(16), dp(4))
        x.setOnClickListener { dlg.dismiss() }
        head.addView(x)
        box.addView(head)
        val grid = GridLayout(this)
        grid.columnCount = 6
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(q, 0)
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        for (ri in apps) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName) continue
            val it = LinearLayout(this)
            it.orientation = LinearLayout.VERTICAL
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.setPadding(dp(6), dp(10), dp(6), dp(10))
            val iv = ImageView(this)
            iv.setImageDrawable(ri.loadIcon(packageManager))
            it.addView(iv, LinearLayout.LayoutParams(dp(52), dp(52)))
            val tv = label(ri.loadLabel(packageManager).toString(), 12f)
            tv.gravity = Gravity.CENTER; tv.maxLines = 1
            it.addView(tv)
            it.setOnClickListener { dlg.dismiss(); launch(listOf(pkg), false) }
            it.setOnLongClickListener { dlg.dismiss(); launch(listOf(pkg), true); true }
            val lp = GridLayout.LayoutParams()
            lp.width = resources.displayMetrics.widthPixels / 6 - dp(4)
            lp.height = W
            grid.addView(it, lp)
        }
        val sv = ScrollView(this)
        sv.addView(grid)
        box.addView(sv)
        dlg.setContentView(box)
        dlg.show()
    }
}

/** Rayas de velocidad: se mueven más rápido mientras más rápido vas. */
class SpeedLines(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = java.util.Random(7)
    private val ys = FloatArray(16) { 0.60f + rnd.nextFloat() * 0.28f }
    private val xs = FloatArray(16) { rnd.nextFloat() }
    private val ls = FloatArray(16) { 0.5f + rnd.nextFloat() }
    private val d = c.resources.displayMetrics.density
    private var kmh = 0f

    init { p.color = Color.WHITE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = d * 3f }

    fun setSpeed(v: Float) { kmh = v; postInvalidateOnAnimation() }

    override fun onDraw(canvas: Canvas) {
        if (kmh < 5f) return
        val w = width * 0.72f
        val step = kmh / 2500f
        p.alpha = (40 + kmh).toInt().coerceAtMost(130)
        for (i in xs.indices) {
            xs[i] -= step * ls[i] * 0.2f
            if (xs[i] < -0.2f) xs[i] += 1.2f
            val x = xs[i] * w
            val y = ys[i] * height
            canvas.drawLine(x, y, x + (20f + kmh * 0.8f) * d * ls[i], y, p)
        }
        postInvalidateOnAnimation()
    }
}
