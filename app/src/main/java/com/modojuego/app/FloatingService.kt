package com.modojuego.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class FloatingService : Service() {

    private class Tile(val vista: LinearLayout, val estado: TextView)

    private lateinit var wm: WindowManager
    private lateinit var burbuja: TextView
    private lateinit var panel: LinearLayout
    private lateinit var hud: TextView
    private lateinit var seccionMira: LinearLayout
    private lateinit var seccionMacro: LinearLayout
    private lateinit var chipFps: TextView
    private lateinit var chipRam: TextView
    private lateinit var chipTemp: TextView
    private lateinit var tileDnd: Tile
    private lateinit var tileHud: Tile
    private lateinit var tileGuide: Tile
    private lateinit var tileMiraSet: Tile
    private lateinit var tileMacro: Tile
    private lateinit var tileBolitaCtrl: Tile
    private lateinit var tileTouch: Tile
    private lateinit var tilePerfil: Tile
    private lateinit var tilePing: Tile
    private lateinit var tileHealth: Tile
    private lateinit var mira: MiraView
    private lateinit var bolitaMacro: View
    private lateinit var fondoBolitaMacro: GradientDrawable
    private lateinit var pBurbuja: WindowManager.LayoutParams
    private lateinit var pPanel: WindowManager.LayoutParams
    private lateinit var pMira: WindowManager.LayoutParams
    private lateinit var pMacro: WindowManager.LayoutParams
    private lateinit var pHud: WindowManager.LayoutParams

    private val botonesModo = mutableListOf<LinearLayout>()
    private val nombresModo = listOf("LOW POWER", "BALANCE", "PERFORMANCE", "EXTREME")

    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("modojuego", MODE_PRIVATE) }
    private var creado = false
    private var panelAbierto = false
    private var miraVisible = false
    private var macroActiva = false
    private var macroVisible = false
    private var macroBloqueada = false
    private var hudActivo = false
    private var ciclo = 0
    private var ultimoPing = -1

    @Volatile
    private var corriendo = false

    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    private val cian = Color.parseColor("#19C6F0")
    private val verde = Color.parseColor("#5CF2A0")
    private val rojo = Color.parseColor("#FF4D5E")
    private val fondoTile = Color.parseColor("#0F1A2E")

    private val opTouch = Herramientas.Opcion(
        "show_touches",
        "TOUCH",
        "settings put system show_touches 1",
        "settings put system show_touches 0"
    )

    private val tipo = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private val banderas = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notificacion(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notificacion())
        }
        if (!creado) {
            creado = true
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            crearBurbuja()
            crearMira()
            crearBolitaMacro()
            crearHud()
            crearPanel()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hudActivo = false
        try { if (panelAbierto) wm.removeView(panel) } catch (e: Throwable) { }
        try { if (miraVisible) wm.removeView(mira) } catch (e: Throwable) { }
        try { if (macroVisible) wm.removeView(bolitaMacro) } catch (e: Throwable) { }
        try { wm.removeView(hud) } catch (e: Throwable) { }
        try { wm.removeView(burbuja) } catch (e: Throwable) { }
        super.onDestroy()
    }

    private fun notificacion(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("modojuego", "Botón flotante", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, "modojuego")
            .setContentTitle("Modo Juego activo")
            .setContentText("Botón flotante en pantalla")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    // ---------- Utilidades ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun aviso(t: String) {
        ui.post { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }
    }

    private fun segundoPlano(accion: () -> Unit) {
        if (!Herramientas.listo()) {
            aviso("Conectá Shizuku primero")
            return
        }
        Thread { accion() }.start()
    }

    private fun nombreColor(): String =
        MiraView.COLORES_NOMBRES[mira.colorIndice.coerceIn(0, MiraView.COLORES_NOMBRES.size - 1)]

    private fun nombrePerfil(): String =
        if (Herramientas.juego(this) == Herramientas.FF_MAX) "MAX" else "FF"

    @Suppress("DEPRECATION")
    private fun hzMaximo(): Float {
        var m = 60f
        for (modo in wm.defaultDisplay.supportedModes) {
            if (modo.refreshRate > m) m = modo.refreshRate
        }
        return m
    }

    @Suppress("DEPRECATION")
    private fun hzActual(): Float = wm.defaultDisplay.refreshRate

    private fun bateria(): Intent? =
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun tempBateria(): Float {
        val t = bateria()?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (t > 0) t / 10f else -1f
    }

    private fun medirPing(): Int {
        val salida = Herramientas.sh("ping -c 1 -W 1 8.8.8.8")
        val m = Regex("time=([0-9.]+)").find(salida) ?: return -1
        return m.groupValues[1].toFloatOrNull()?.toInt() ?: -1
    }

    private fun resumenBateria() {
        val i = bateria()
        if (i == null) {
            aviso("No pude leer la batería")
            return
        }
        val nivel = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val escala = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (nivel >= 0 && escala > 0) nivel * 100 / escala else -1
        val salud = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "buena"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "sobrecalentada"
            BatteryManager.BATTERY_HEALTH_COLD -> "fría"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dañada"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "sobretensión"
            else -> "desconocida"
        }
        val t = tempBateria()
        val tTxt = if (t > 0) String.format(Locale.US, "%.1f°C", t) else "--"
        aviso("Batería $pct% · $tTxt · salud $salud")
    }

    // ---------- Modos (frecuencia de pantalla) ----------

    private fun aplicarModo(modo: Int) {
        if (!Herramientas.listo()) {
            aviso("Conectá Shizuku primero")
            return
        }
        prefs.edit().putInt("modo", modo).apply()
        pintarModos(modo)
        Thread {
            val maxHz = hzMaximo()
            val max = String.format(Locale.US, "%.1f", maxHz)
            val comando = when (modo) {
                0 -> "settings put system min_refresh_rate 60.0; " +
                    "settings put system peak_refresh_rate 60.0"
                1 -> "settings delete system min_refresh_rate; " +
                    "settings delete system peak_refresh_rate"
                else -> "settings put system min_refresh_rate $max; " +
                    "settings put system peak_refresh_rate $max"
            }
            Herramientas.sh(comando)
            if (modo == 3) {
                Herramientas.limpiarRam()
                val dnd = Herramientas.opciones.first { it.id == "dnd" }
                Herramientas.poner(this, dnd, true)
                ui.post { marcar(tileDnd, true, "ON") }
            }
            Thread.sleep(1500)
            val ahora = hzActual()
            val ahoraTxt = String.format(Locale.US, "%.0f Hz", ahora)
            val ignorado = (modo >= 2 && ahora < maxHz - 1f) || (modo == 0 && ahora > 61f)
            if (ignorado) {
                aviso("Tu celular ignoró el ajuste: la pantalla sigue en $ahoraTxt")
            } else {
                aviso(nombresModo[modo] + " ✔ (pantalla en " + ahoraTxt + ")")
            }
        }.start()
    }

    private fun pintarModos(sel: Int) {
        for (k in botonesModo.indices) {
            val g = GradientDrawable()
            g.cornerRadius = dp(10).toFloat()
            if (k == sel) {
                g.setColor(Color.parseColor("#12384F"))
                g.setStroke(dp(2), cian)
            } else {
                g.setColor(fondoTile)
                g.setStroke(dp(1), Color.parseColor("#2A3550"))
            }
            botonesModo[k].background = g
        }
    }

    // ---------- Burbuja ----------

    private fun crearBurbuja() {
        burbuja = TextView(this)
        burbuja.text = "⚡"
        burbuja.textSize = 22f
        burbuja.setTextColor(Color.WHITE)
        burbuja.gravity = Gravity.CENTER
        val circulo = GradientDrawable()
        circulo.shape = GradientDrawable.OVAL
        circulo.setColor(Color.parseColor("#CC0A1326"))
        circulo.setStroke(dp(2), cian)
        burbuja.background = circulo

        val tam = dp(52)
        pBurbuja = WindowManager.LayoutParams(tam, tam, tipo, banderas, PixelFormat.TRANSLUCENT)
        pBurbuja.gravity = Gravity.TOP or Gravity.START
        pBurbuja.x = dp(8)
        pBurbuja.y = dp(120)

        burbuja.setOnTouchListener(object : View.OnTouchListener {
            var x0 = 0
            var y0 = 0
            var tx = 0f
            var ty = 0f
            var movio = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        x0 = pBurbuja.x
                        y0 = pBurbuja.y
                        tx = e.rawX
                        ty = e.rawY
                        movio = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (!movio && (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6))) {
                            movio = true
                            if (panelAbierto) cerrarPanel()
                        }
                        if (movio) {
                            pBurbuja.x = x0 + dx
                            pBurbuja.y = y0 + dy
                            wm.updateViewLayout(burbuja, pBurbuja)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!movio) alternarPanel()
                    }
                }
                return true
            }
        })

        wm.addView(burbuja, pBurbuja)
    }

    // ---------- Mira ----------

    private fun crearMira() {
        mira = MiraView(this)
        mira.diseno = prefs.getInt("mira_diseno", 0)
        mira.colorIndice = prefs.getInt("mira_color", 0)
        val px = dp(prefs.getInt("mira_tam", 80))
        val banderasMira = banderas or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        pMira = WindowManager.LayoutParams(px, px, tipo, banderasMira, PixelFormat.TRANSLUCENT)
        pMira.gravity = Gravity.CENTER
    }

    private fun aplicarTamanoMira() {
        val px = dp(prefs.getInt("mira_tam", 80))
        pMira.width = px
        pMira.height = px
        if (miraVisible) wm.updateViewLayout(mira, pMira)
    }

    private fun mostrarMira(mostrar: Boolean) {
        if (mostrar == miraVisible) return
        if (mostrar) {
            aplicarTamanoMira()
            wm.addView(mira, pMira)
        } else {
            wm.removeView(mira)
        }
        miraVisible = mostrar
    }

    // ---------- Bolita Macro ----------

    private fun crearBolitaMacro() {
        macroBloqueada = prefs.getBoolean("macro_bloqueada", false)
        val tamDp = prefs.getInt("macro_tam", 72).coerceIn(24, 1000)
        val grosorDp = prefs.getInt("macro_grosor", 3).coerceIn(1, 20)
        val alphaPct = prefs.getInt("macro_alpha", 70).coerceIn(10, 100)

        bolitaMacro = View(this)
        fondoBolitaMacro = GradientDrawable()
        fondoBolitaMacro.shape = GradientDrawable.OVAL
        fondoBolitaMacro.setColor(Color.parseColor("#3319C6F0"))
        fondoBolitaMacro.setStroke(dp(grosorDp), cian)
        bolitaMacro.background = fondoBolitaMacro
        bolitaMacro.alpha = alphaPct / 100f

        val px = dp(tamDp)
        var flagsMacro = banderas or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (macroBloqueada) {
            flagsMacro = flagsMacro or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        pMacro = WindowManager.LayoutParams(px, px, tipo, flagsMacro, PixelFormat.TRANSLUCENT)
        pMacro.gravity = Gravity.TOP or Gravity.START
        pMacro.x = prefs.getInt("macro_x", dp(160))
        pMacro.y = prefs.getInt("macro_y", dp(160))

        bolitaMacro.setOnTouchListener(object : View.OnTouchListener {
            var x0 = 0
            var y0 = 0
            var tx = 0f
            var ty = 0f

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                if (macroBloqueada) return false
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        x0 = pMacro.x
                        y0 = pMacro.y
                        tx = e.rawX
                        ty = e.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        pMacro.x = x0 + (e.rawX - tx).toInt()
                        pMacro.y = y0 + (e.rawY - ty).toInt()
                        wm.updateViewLayout(bolitaMacro, pMacro)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        prefs.edit()
                            .putInt("macro_x", pMacro.x)
                            .putInt("macro_y", pMacro.y)
                            .apply()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun mostrarBolitaMacro(mostrar: Boolean) {
        if (mostrar == macroVisible) return
        if (mostrar) {
            aplicarEstiloBolitaMacro()
            wm.addView(bolitaMacro, pMacro)
        } else {
            wm.removeView(bolitaMacro)
        }
        macroVisible = mostrar
    }

    private fun actualizarBotonIndependienteBolita() {
        if (!::tileBolitaCtrl.isInitialized) return
        if (!macroActiva) {
            tileBolitaCtrl.vista.alpha = 0.45f
            marcar(tileBolitaCtrl, false, "OFF")
            return
        }
        tileBolitaCtrl.vista.alpha = 1f
        when {
            !macroVisible -> marcar(tileBolitaCtrl, false, "HIDE")
            macroBloqueada -> marcar(tileBolitaCtrl, true, "LOCK")
            else -> marcar(tileBolitaCtrl, true, "FREE")
        }
    }

    private fun aplicarBloqueoMacro(bloqueada: Boolean) {
        macroBloqueada = bloqueada
        prefs.edit().putBoolean("macro_bloqueada", bloqueada).apply()
        if (bloqueada) {
            pMacro.flags = pMacro.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            pMacro.flags = pMacro.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        if (macroVisible) {
            wm.updateViewLayout(bolitaMacro, pMacro)
        }
        actualizarBotonIndependienteBolita()
    }

    private fun aplicarEstiloBolitaMacro() {
        val tamDp = prefs.getInt("macro_tam", 72).coerceIn(24, 1000)
        val grosorDp = prefs.getInt("macro_grosor", 3).coerceIn(1, 20)
        val alphaPct = prefs.getInt("macro_alpha", 70).coerceIn(10, 100)

        val px = dp(tamDp)
        pMacro.width = px
        pMacro.height = px
        fondoBolitaMacro.setStroke(dp(grosorDp), cian)
        bolitaMacro.alpha = alphaPct / 100f

        if (macroVisible) {
            wm.updateViewLayout(bolitaMacro, pMacro)
        }
    }

    // ---------- HUD (FPS | temperatura | ping) ----------

    private fun crearHud() {
        hud = TextView(this)
        hud.text = "-- FPS | --°C | --ms"
        hud.textSize = 13f
        hud.setTextColor(verde)
        hud.setPadding(dp(10), dp(6), dp(10), dp(6))
        val g = GradientDrawable()
        g.setColor(Color.parseColor("#CC0A1326"))
        g.cornerRadius = dp(6).toFloat()
        g.setStroke(dp(1), cian)
        hud.background = g

        pHud = WindowManager.LayoutParams(
            WRAP,
            WRAP,
            tipo,
            banderas or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        pHud.gravity = Gravity.TOP or Gravity.START
        pHud.x = dp(8)
        pHud.y = dp(28)
    }

    private fun asegurarLoop() {
        if (corriendo) return
        corriendo = true
        Thread {
            while (hudActivo || panelAbierto) {
                val listo = Herramientas.listo()
                val ram = Herramientas.ramUso(this).first
                val fps = if (listo) Herramientas.fps(Herramientas.juego(this)) else -1
                val temp = tempBateria()
                ciclo++
                if (listo && ciclo % 3 == 0) ultimoPing = medirPing()
                val ping = ultimoPing
                ui.post { pintarMonitor(fps, ram, temp, ping) }
                Thread.sleep(1000)
            }
            corriendo = false
        }.start()
    }

    private fun pintarMonitor(fps: Int, ram: Int, temp: Float, ping: Int) {
        val f = if (fps >= 0) "$fps" else "--"
        val t = if (temp > 0) String.format(Locale.US, "%.1f", temp) else "--"
        val p = if (ping >= 0) "$ping" else "--"
        chipFps.text = "$f FPS"
        chipRam.text = "RAM $ram%"
        chipTemp.text = "${t}°C • ${p}ms"
        hud.text = "$f FPS | ${t}°C | ${p}ms"
        tileHealth.estado.text = "${t}°C"
        tilePing.estado.text = "$p ms"
    }

    // ---------- Piezas de diseño ----------

    private fun pildora(radio: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.parseColor("#1A2A44"))
        g.cornerRadius = dp(radio).toFloat()
        g.setStroke(dp(1), Color.parseColor("#5019C6F0"))
        return g
    }

    private fun paramsFila(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(8)
        return lp
    }

    private fun valorTexto(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 14f
        v.setTextColor(Color.WHITE)
        return v
    }

    private fun fila(texto: String, derecha: View, alTocar: () -> Unit): LinearLayout {
        val f = LinearLayout(this)
        f.orientation = LinearLayout.HORIZONTAL
        f.gravity = Gravity.CENTER_VERTICAL
        f.setPadding(dp(16), dp(10), dp(12), dp(10))
        f.background = pildora(14)
        val t = TextView(this)
        t.text = texto
        t.setTextColor(Color.WHITE)
        t.textSize = 15f
        f.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        f.addView(derecha)
        f.layoutParams = paramsFila()
        f.setOnClickListener { alTocar() }
        return f
    }

    private fun tarjetaSlider(
        titulo: String,
        valor: Int,
        min: Int,
        max: Int,
        alCambiar: (Int) -> Unit
    ): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(12), dp(16), dp(8))
        c.background = pildora(14)

        val arriba = LinearLayout(this)
        arriba.orientation = LinearLayout.HORIZONTAL
        val t = TextView(this)
        t.text = titulo
        t.setTextColor(Color.WHITE)
        t.textSize = 15f
        arriba.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        val v = TextView(this)
        v.text = "$valor"
        v.setTextColor(Color.WHITE)
        v.textSize = 15f
        arriba.addView(v)
        c.addView(arriba)

        val barra = SeekBar(this)
        barra.max = max - min
        barra.progress = valor - min
        barra.progressTintList = ColorStateList.valueOf(cian)
        barra.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        barra.progressBackgroundTintList =
            ColorStateList.valueOf(Color.parseColor("#66FFFFFF"))
        barra.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progreso: Int, usuario: Boolean) {
                val nuevo = min + progreso
                v.text = "$nuevo"
                alCambiar(nuevo)
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        c.addView(barra)
        c.layoutParams = paramsFila()
        return c
    }

    private fun botonCuadro(t: String, color: Int, alTocar: () -> Unit): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 14f
        v.gravity = Gravity.CENTER
        v.setTextColor(Color.WHITE)
        v.setTypeface(v.typeface, Typeface.BOLD)
        val g = GradientDrawable()
        g.setColor(Color.parseColor("#0F1A2E"))
        g.cornerRadius = dp(8).toFloat()
        g.setStroke(dp(2), color)
        v.background = g
        v.setOnClickListener { alTocar() }
        val lp = LinearLayout.LayoutParams(dp(34), dp(34))
        lp.marginStart = dp(6)
        v.layoutParams = lp
        return v
    }

    private fun chip(t: String, colorTexto: Int): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 11f
        v.gravity = Gravity.CENTER
        v.setTextColor(colorTexto)
        v.setTypeface(v.typeface, Typeface.BOLD)
        v.setSingleLine(true)
        v.setPadding(dp(4), dp(7), dp(4), dp(7))
        val g = GradientDrawable()
        g.setColor(Color.parseColor("#0F1A2E"))
        g.cornerRadius = dp(8).toFloat()
        g.setStroke(dp(1), cian)
        v.background = g
        return v
    }

    private fun botonModo(nombre: String, sub: String, alTocar: () -> Unit): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(dp(2), dp(8), dp(2), dp(8))
        val a = TextView(this)
        a.text = nombre
        a.textSize = 10f
        a.gravity = Gravity.CENTER
        a.setSingleLine(true)
        a.setTextColor(Color.WHITE)
        a.setTypeface(a.typeface, Typeface.BOLD)
        val b = TextView(this)
        b.text = sub
        b.textSize = 9f
        b.gravity = Gravity.CENTER
        b.setTextColor(Color.parseColor("#8A93A6"))
        c.addView(a)
        c.addView(b)
        c.setOnClickListener { alTocar() }
        return c
    }

    private fun tile(icono: String, nombre: String, estado: String, alTocar: () -> Unit): Tile {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(dp(2), dp(8), dp(2), dp(8))

        val i = TextView(this)
        i.text = icono
        i.textSize = 18f
        i.gravity = Gravity.CENTER

        val n = TextView(this)
        n.text = nombre
        n.textSize = 10f
        n.gravity = Gravity.CENTER
        n.setSingleLine(true)
        n.setTextColor(Color.WHITE)
        n.setTypeface(n.typeface, Typeface.BOLD)

        val e = TextView(this)
        e.text = estado
        e.textSize = 9f
        e.gravity = Gravity.CENTER
        e.setSingleLine(true)

        c.addView(i)
        c.addView(n)
        c.addView(e)
        c.setOnClickListener { alTocar() }

        val t = Tile(c, e)
        marcar(t, false, estado)
        return t
    }

    private fun marcar(t: Tile, activo: Boolean, texto: String) {
        t.estado.text = texto
        t.estado.setTextColor(if (activo) cian else Color.parseColor("#8A93A6"))
        val g = GradientDrawable()
        g.cornerRadius = dp(10).toFloat()
        if (activo) {
            g.setColor(Color.parseColor("#12384F"))
            g.setStroke(dp(2), cian)
        } else {
            g.setColor(fondoTile)
            g.setStroke(dp(1), Color.parseColor("#2A3550"))
        }
        t.vista.background = g
    }

    private fun filaTiles(vararg ts: Tile): LinearLayout {
        val f = LinearLayout(this)
        f.orientation = LinearLayout.HORIZONTAL
        for (k in ts.indices) {
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.marginEnd = if (k == ts.size - 1) 0 else dp(4)
            f.addView(ts[k].vista, lp)
        }
        f.layoutParams = paramsFila()
        return f
    }

    // ---------- Panel ----------

    private fun crearPanel() {
        val dnd = Herramientas.opciones.first { it.id == "dnd" }

        // Cabecera: título + minimizar + cerrar
        val cab = LinearLayout(this)
        cab.orientation = LinearLayout.HORIZONTAL
        cab.gravity = Gravity.CENTER_VERTICAL
        val titulo = TextView(this)
        titulo.text = "◆ FERMXN CORE"
        titulo.textSize = 16f
        titulo.setTextColor(Color.WHITE)
        titulo.setTypeface(titulo.typeface, Typeface.BOLD)
        cab.addView(titulo, LinearLayout.LayoutParams(0, WRAP, 1f))
        cab.addView(botonCuadro("▼", cian) { cerrarPanel() })
        cab.addView(botonCuadro("X", rojo) { stopSelf() })

        // Chips: FPS, RAM, temperatura y ping
        chipFps = chip("-- FPS", verde)
        chipRam = chip("RAM --%", Color.WHITE)
        chipTemp = chip("--°C • --ms", Color.WHITE)
        val filaChips = LinearLayout(this)
        filaChips.orientation = LinearLayout.HORIZONTAL
        val lp1 = LinearLayout.LayoutParams(0, WRAP, 1f)
        lp1.marginEnd = dp(4)
        val lp2 = LinearLayout.LayoutParams(0, WRAP, 1f)
        lp2.marginEnd = dp(4)
        val lp3 = LinearLayout.LayoutParams(0, WRAP, 1.3f)
        filaChips.addView(chipFps, lp1)
        filaChips.addView(chipRam, lp2)
        filaChips.addView(chipTemp, lp3)
        filaChips.layoutParams = paramsFila()

        // Modos
        val hzTxt = String.format(Locale.US, "%.0fHz", hzMaximo())
        val subs = listOf("60Hz", "Auto", hzTxt, "$hzTxt+")
        val filaModos = LinearLayout(this)
        filaModos.orientation = LinearLayout.HORIZONTAL
        for (k in nombresModo.indices) {
            val b = botonModo(nombresModo[k], subs[k]) { aplicarModo(k) }
            botonesModo.add(b)
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.marginEnd = if (k == nombresModo.size - 1) 0 else dp(4)
            filaModos.addView(b, lp)
        }
        filaModos.layoutParams = paramsFila()
        pintarModos(prefs.getInt("modo", 1).coerceIn(0, 3))

        // Botones de la cuadrícula
        val tBoost = tile("🚀", "BOOST", "CLEAN") {
            segundoPlano {
                Herramientas.limpiarRam()
                aviso("RAM limpiada ✔")
            }
        }
        val tCache = tile("🗑", "CACHE", "CLEAN") {
            segundoPlano {
                Herramientas.borrarCache()
                aviso("Caché borrada ✔")
            }
        }
        tileDnd = tile("🔕", "DND", "OFF") {
            segundoPlano {
                val nuevo = !Herramientas.activa(this, dnd.id)
                Herramientas.poner(this, dnd, nuevo)
                ui.post { marcar(tileDnd, nuevo, if (nuevo) "ON" else "OFF") }
            }
        }
        val dndOn = Herramientas.activa(this, dnd.id)
        marcar(tileDnd, dndOn, if (dndOn) "ON" else "OFF")

        tileHud = tile("📊", "FPS HUD", "OFF") {
            if (!Herramientas.listo()) {
                aviso("Conectá Shizuku primero")
            } else {
                hudActivo = !hudActivo
                if (hudActivo) wm.addView(hud, pHud) else wm.removeView(hud)
                marcar(tileHud, hudActivo, if (hudActivo) "ON" else "OFF")
                asegurarLoop()
            }
        }
        val tShot = tile("📸", "SHOT", "SNAP") {
            segundoPlano {
                ui.post { cerrarPanel() }
                Thread.sleep(800)
                Herramientas.sh("input keyevent 120")
                aviso("Captura tomada 📸")
            }
        }
        val tMusic = tile("🎵", "MUSIC", "PLAY/PAUSE") {
            segundoPlano {
                Herramientas.sh("input keyevent 85")
                aviso("Música ⏯")
            }
        }

        tileGuide = tile("🎯", "GUIDE", "OFF") {
            mostrarMira(!miraVisible)
            marcar(tileGuide, miraVisible, if (miraVisible) "ON" else "OFF")
        }
        tileMiraSet = tile("⚙", "MIRA", "SET") {
            val ver = seccionMira.visibility != View.VISIBLE
            seccionMira.visibility = if (ver) View.VISIBLE else View.GONE
            marcar(tileMiraSet, ver, if (ver) "OPEN" else "SET")
        }
        tileMacro = tile("🔵", "MACRO", "OFF") {
            macroActiva = !macroActiva
            if (macroActiva) {
                mostrarBolitaMacro(true)
                seccionMacro.visibility = View.VISIBLE
                marcar(tileMacro, true, "ON")
            } else {
                mostrarBolitaMacro(false)
                seccionMacro.visibility = View.GONE
                marcar(tileMacro, false, "OFF")
            }
            actualizarBotonIndependienteBolita()
        }
        tileBolitaCtrl = tile("🔒", "BOLITA", "OFF") {
            if (!macroActiva) {
                aviso("Activá MACRO primero")
                return@tile
            }
            when {
                !macroVisible -> {
                    mostrarBolitaMacro(true)
                    aplicarBloqueoMacro(false)
                    aviso("Bolita visible y libre")
                }
                !macroBloqueada -> {
                    aplicarBloqueoMacro(true)
                    aviso("Posición de bolita bloqueada")
                }
                else -> {
                    aplicarBloqueoMacro(false)
                    mostrarBolitaMacro(false)
                    aviso("Bolita oculta")
                }
            }
            actualizarBotonIndependienteBolita()
        }
        actualizarBotonIndependienteBolita()

        tileTouch = tile("👆", "TOUCH", "OFF") {
            segundoPlano {
                val nuevo = !Herramientas.activa(this, opTouch.id)
                Herramientas.poner(this, opTouch, nuevo)
                ui.post { marcar(tileTouch, nuevo, if (nuevo) "ON" else "OFF") }
            }
        }
        val touchOn = Herramientas.activa(this, opTouch.id)
        marcar(tileTouch, touchOn, if (touchOn) "ON" else "OFF")

        tilePerfil = tile("🎮", "PROFILE", "MAX") {
            val nuevo = if (Herramientas.juego(this) == Herramientas.FF_MAX) {
                Herramientas.FF_NORMAL
            } else {
                Herramientas.FF_MAX
            }
            Herramientas.elegirJuego(this, nuevo)
            marcar(tilePerfil, true, nombrePerfil())
            aviso("Juego: " + (if (nuevo == Herramientas.FF_MAX) "Free Fire MAX" else "Free Fire"))
        }
        marcar(tilePerfil, true, nombrePerfil())

        tilePing = tile("📶", "PING", "-- ms") {
            segundoPlano {
                val p = medirPing()
                ui.post { tilePing.estado.text = if (p >= 0) "$p ms" else "-- ms" }
            }
        }
        tileHealth = tile("🔋", "HEALTH", "--°C") { resumenBateria() }

        // Ajustes de la mira (se abre con el botón MIRA)
        seccionMira = LinearLayout(this)
        seccionMira.orientation = LinearLayout.VERTICAL
        seccionMira.visibility = View.GONE

        val vDiseno = valorTexto(MiraView.NOMBRES[mira.diseno] + "  ⌄")
        seccionMira.addView(fila("Diseño", vDiseno) {
            val n = (mira.diseno + 1) % MiraView.NOMBRES.size
            mira.diseno = n
            prefs.edit().putInt("mira_diseno", n).apply()
            vDiseno.text = MiraView.NOMBRES[n] + "  ⌄"
        })
        val vColor = valorTexto(nombreColor() + "  ⌄")
        seccionMira.addView(fila("Color", vColor) {
            val n = (mira.colorIndice + 1) % MiraView.COLORES.size
            mira.colorIndice = n
            prefs.edit().putInt("mira_color", n).apply()
            vColor.text = nombreColor() + "  ⌄"
        })
        val tamActual = prefs.getInt("mira_tam", 80).coerceIn(24, 1000)
        seccionMira.addView(tarjetaSlider("Tamaño de la mira", tamActual, 24, 1000) { v ->
            prefs.edit().putInt("mira_tam", v).apply()
            aplicarTamanoMira()
        })

        // Ajustes de la bolita Macro (se abre al activar MACRO)
        seccionMacro = LinearLayout(this)
        seccionMacro.orientation = LinearLayout.VERTICAL
        seccionMacro.visibility = View.GONE

        val vBloqueo = valorTexto(if (macroBloqueada) "LOCK 🔒" else "FREE 🔓")
        seccionMacro.addView(fila("Posición de bolita", vBloqueo) {
            val nuevoBloqueo = !macroBloqueada
            aplicarBloqueoMacro(nuevoBloqueo)
            vBloqueo.text = if (nuevoBloqueo) "LOCK 🔒" else "FREE 🔓"
        })

        val tamMacroActual = prefs.getInt("macro_tam", 72).coerceIn(24, 1000)
        seccionMacro.addView(tarjetaSlider("Tamaño de bolita", tamMacroActual, 24, 1000) { v ->
            prefs.edit().putInt("macro_tam", v).apply()
            aplicarEstiloBolitaMacro()
        })

        val grosorMacroActual = prefs.getInt("macro_grosor", 3).coerceIn(1, 20)
        seccionMacro.addView(tarjetaSlider("Grosor de bolita", grosorMacroActual, 1, 20) { v ->
            prefs.edit().putInt("macro_grosor", v).apply()
            aplicarEstiloBolitaMacro()
        })

        val alphaMacroActual = prefs.getInt("macro_alpha", 70).coerceIn(10, 100)
        seccionMacro.addView(tarjetaSlider("Transparencia bolita (%)", alphaMacroActual, 10, 100) { v ->
            prefs.edit().putInt("macro_alpha", v).apply()
            aplicarEstiloBolitaMacro()
        })

        // Contenido con scroll
        val contenido = LinearLayout(this)
        contenido.orientation = LinearLayout.VERTICAL
        contenido.addView(filaTiles(tBoost, tCache, tileDnd, tileHud, tShot, tMusic))
        contenido.addView(filaTiles(tileGuide, tileMiraSet, tileMacro, tileBolitaCtrl, tileTouch, tilePerfil))
        contenido.addView(filaTiles(tilePing, tileHealth))
        contenido.addView(seccionMira, paramsFila())
        contenido.addView(seccionMacro, paramsFila())

        val scroll = ScrollView(this)
        scroll.addView(contenido)

        // Panel completo
        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(dp(10), dp(10), dp(10), dp(10))
        val fondoPanel = GradientDrawable()
        fondoPanel.setColor(Color.parseColor("#F20A1326"))
        fondoPanel.cornerRadius = dp(20).toFloat()
        fondoPanel.setStroke(dp(2), cian)
        raiz.background = fondoPanel
        raiz.addView(cab, LinearLayout.LayoutParams(MATCH, WRAP))
        raiz.addView(filaChips)
        raiz.addView(filaModos)
        raiz.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        panel = raiz

        pPanel = WindowManager.LayoutParams(
            dp(360),
            dp(300),
            tipo,
            banderas,
            PixelFormat.TRANSLUCENT
        )
        pPanel.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    }

    private fun alternarPanel() {
        if (panelAbierto) cerrarPanel() else abrirPanel()
    }

    private fun cerrarPanel() {
        if (!panelAbierto) return
        try { wm.removeView(panel) } catch (e: Throwable) { }
        panelAbierto = false
    }

    private fun abrirPanel() {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)

        pPanel.width = minOf(dp(620), (dm.widthPixels * 0.96).toInt())
        pPanel.height = minOf(dp(330), (dm.heightPixels * 0.88).toInt())
        pPanel.x = 0
        pPanel.y = if (dm.heightPixels < dm.widthPixels) dp(6) else dp(40)

        wm.addView(panel, pPanel)
        panelAbierto = true
        asegurarLoop()

        // La burbuja queda siempre por encima del panel
        ui.post {
            try {
                wm.removeView(burbuja)
                wm.addView(burbuja, pBurbuja)
            } catch (e: Throwable) { }
        }
    }
}
