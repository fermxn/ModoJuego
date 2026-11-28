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

    private data class ColorMacro(val nombre: String, val borde: Int, val relleno: Int)

    private lateinit var wm: WindowManager
    private lateinit var burbuja: TextView
    private lateinit var panel: LinearLayout
    private lateinit var hud: TextView
    private lateinit var seccionMira: LinearLayout
    private lateinit var seccionMacro: LinearLayout
    private lateinit var tabModosHud: LinearLayout
    private lateinit var tabMira: LinearLayout
    private lateinit var tabMacro: LinearLayout
    private lateinit var tabOptimizar: LinearLayout
    private lateinit var tabRapido: LinearLayout
    private val iconosPestanas = mutableListOf<TextView>()
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

    private val coloresMacro = listOf(
        ColorMacro("CIAN", Color.parseColor("#19C6F0"), Color.parseColor("#3319C6F0")),
        ColorMacro("AZUL", Color.parseColor("#2196F3"), Color.parseColor("#332196F3")),
        ColorMacro("VERDE", Color.parseColor("#5CF2A0"), Color.parseColor("#335CF2A0")),
        ColorMacro("ROJO", Color.parseColor("#FF4D5E"), Color.parseColor("#33FF4D5E")),
        ColorMacro("MORADO", Color.parseColor("#B366FF"), Color.parseColor("#33B366FF")),
        ColorMacro("AMARILLO", Color.parseColor("#FFD54F"), Color.parseColor("#33FFD54F")),
        ColorMacro("NARANJA", Color.parseColor("#FF8A3D"), Color.parseColor("#33FF8A3D")),
        ColorMacro("BLANCO", Color.parseColor("#FFFFFF"), Color.parseColor("#33FFFFFF"))
    )

    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("modojuego", MODE_PRIVATE) }
    private var creado = false
    private var panelAbierto = false
    private var pestanaActiva = 0
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
    private val fondoTile = Color.parseColor("#663A3E44")
    private val grisPanel = Color.parseColor("#CC5A5E65")
    private val grisRail = Color.parseColor("#B84A4E54")
    private val grisCapsula = Color.parseColor("#66484C52")
    private val bordeSuave = Color.parseColor("#99A5ACB5")

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

    private fun colorMacroActual(): ColorMacro {
        val idx = prefs.getInt("macro_color", 0).coerceIn(0, coloresMacro.lastIndex)
        return coloresMacro[idx]
    }

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
            g.cornerRadius = dp(16).toFloat()
            if (k == sel) {
                g.setColor(Color.parseColor("#887E8692"))
                g.setStroke(dp(2), Color.WHITE)
            } else {
                g.setColor(grisCapsula)
                g.setStroke(dp(1), bordeSuave)
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
        circulo.setColor(Color.parseColor("#B85A5E65"))
        circulo.setStroke(dp(2), Color.WHITE)
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
        macroBloqueada = prefs.getBoolean("macro_bloqueada", true)
        val colorActual = colorMacroActual()
        val tamDp = prefs.getInt("macro_tam", 72).coerceIn(24, 1000)
        val grosorDp = prefs.getInt("macro_grosor", 3).coerceIn(1, 20)
        val alphaPct = prefs.getInt("macro_alpha", 70).coerceIn(10, 100)

        bolitaMacro = View(this)
        fondoBolitaMacro = GradientDrawable()
        fondoBolitaMacro.shape = GradientDrawable.OVAL
        fondoBolitaMacro.setColor(colorActual.relleno)
        fondoBolitaMacro.setStroke(dp(grosorDp), colorActual.borde)
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
            macroBloqueada -> marcar(tileBolitaCtrl, true, "PASS")
            else -> marcar(tileBolitaCtrl, true, "MOVE")
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
        val colorActual = colorMacroActual()
        val tamDp = prefs.getInt("macro_tam", 72).coerceIn(24, 1000)
        val grosorDp = prefs.getInt("macro_grosor", 3).coerceIn(1, 20)
        val alphaPct = prefs.getInt("macro_alpha", 70).coerceIn(10, 100)

        val px = dp(tamDp)
        pMacro.width = px
        pMacro.height = px
        fondoBolitaMacro.setColor(colorActual.relleno)
        fondoBolitaMacro.setStroke(dp(grosorDp), colorActual.borde)
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
        g.setColor(Color.parseColor("#CC3E4248"))
        g.cornerRadius = dp(12).toFloat()
        g.setStroke(dp(1), bordeSuave)
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

    // ---------- Piezas de diseño estilo panel flotante ----------

    private fun pildora(radio: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(grisCapsula)
        g.cornerRadius = dp(radio).toFloat()
        g.setStroke(dp(1), bordeSuave)
        return g
    }

    private fun paramsFila(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(8)
        return lp
    }

    private fun subtituloSeccion(texto: String): TextView {
        val tv = TextView(this)
        tv.text = texto.uppercase(Locale.US)
        tv.textSize = 11f
        tv.setTextColor(Color.parseColor("#D6DCE4"))
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.letterSpacing = 0.08f
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(10)
        lp.bottomMargin = dp(2)
        lp.marginStart = dp(4)
        tv.layoutParams = lp
        return tv
    }

    private fun indicadorCheck(activo: Boolean): TextView {
        val v = TextView(this)
        v.gravity = Gravity.CENTER
        v.textSize = 13f
        v.setTypeface(v.typeface, Typeface.BOLD)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        if (activo) {
            v.text = "✓"
            v.setTextColor(Color.WHITE)
            g.setColor(Color.parseColor("#8C929C"))
            g.setStroke(dp(1), Color.WHITE)
        } else {
            v.text = ""
            v.setTextColor(Color.TRANSPARENT)
            g.setColor(Color.parseColor("#33383E44"))
            g.setStroke(dp(1), bordeSuave)
        }
        v.background = g
        v.layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
        return v
    }

    private fun valorTexto(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 13f
        v.setTextColor(Color.WHITE)
        v.setTypeface(v.typeface, Typeface.BOLD)
        return v
    }

    private fun fila(texto: String, derecha: View, alTocar: () -> Unit): LinearLayout {
        val f = LinearLayout(this)
        f.orientation = LinearLayout.HORIZONTAL
        f.gravity = Gravity.CENTER_VERTICAL
        f.setPadding(dp(16), dp(11), dp(14), dp(11))
        f.background = pildora(22)
        val t = TextView(this)
        t.text = texto
        t.setTextColor(Color.WHITE)
        t.textSize = 14f
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
        c.background = pildora(20)

        val arriba = LinearLayout(this)
        arriba.orientation = LinearLayout.HORIZONTAL
        val t = TextView(this)
        t.text = titulo
        t.setTextColor(Color.WHITE)
        t.textSize = 14f
        arriba.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        val v = TextView(this)
        v.text = "$valor"
        v.setTextColor(Color.WHITE)
        v.textSize = 14f
        arriba.addView(v)
        c.addView(arriba)

        val barra = SeekBar(this)
        barra.max = max - min
        barra.progress = valor - min
        barra.progressTintList = ColorStateList.valueOf(Color.WHITE)
        barra.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        barra.progressBackgroundTintList =
            ColorStateList.valueOf(Color.parseColor("#55FFFFFF"))
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
        v.textSize = 13f
        v.gravity = Gravity.CENTER
        v.setTextColor(Color.WHITE)
        v.setTypeface(v.typeface, Typeface.BOLD)
        val g = GradientDrawable()
        g.setColor(grisCapsula)
        g.cornerRadius = dp(12).toFloat()
        g.setStroke(dp(1), color)
        v.background = g
        v.setOnClickListener { alTocar() }
        val lp = LinearLayout.LayoutParams(dp(32), dp(32))
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
        v.setPadding(dp(6), dp(7), dp(6), dp(7))
        val g = GradientDrawable()
        g.setColor(grisCapsula)
        g.cornerRadius = dp(14).toFloat()
        g.setStroke(dp(1), bordeSuave)
        v.background = g
        return v
    }

    private fun botonModo(nombre: String, sub: String, alTocar: () -> Unit): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(dp(4), dp(8), dp(4), dp(8))
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
        b.setTextColor(Color.parseColor("#E0E4EA"))
        c.addView(a)
        c.addView(b)
        c.setOnClickListener { alTocar() }
        return c
    }

    private fun tile(icono: String, nombre: String, estado: String, alTocar: () -> Unit): Tile {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(dp(4), dp(9), dp(4), dp(9))

        val i = TextView(this)
        i.text = icono
        i.textSize = 17f
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
        t.estado.setTextColor(if (activo) Color.WHITE else Color.parseColor("#D8DEE6"))
        val g = GradientDrawable()
        g.cornerRadius = dp(16).toFloat()
        if (activo) {
            g.setColor(Color.parseColor("#88828A96"))
            g.setStroke(dp(2), Color.WHITE)
        } else {
            g.setColor(fondoTile)
            g.setStroke(dp(1), bordeSuave)
        }
        t.vista.background = g
    }

    private fun filaTiles(vararg ts: Tile): LinearLayout {
        val f = LinearLayout(this)
        f.orientation = LinearLayout.HORIZONTAL
        for (k in ts.indices) {
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.marginEnd = if (k == ts.size - 1) 0 else dp(6)
            f.addView(ts[k].vista, lp)
        }
        f.layoutParams = paramsFila()
        return f
    }

    private fun seleccionarPestana(indice: Int) {
        pestanaActiva = indice
        tabModosHud.visibility = if (indice == 0) View.VISIBLE else View.GONE
        tabMira.visibility = if (indice == 1) View.VISIBLE else View.GONE
        tabMacro.visibility = if (indice == 2) View.VISIBLE else View.GONE
        tabOptimizar.visibility = if (indice == 3) View.VISIBLE else View.GONE
        tabRapido.visibility = if (indice == 4) View.VISIBLE else View.GONE

        for (i in iconosPestanas.indices) {
            val tv = iconosPestanas[i]
            val activo = i == indice
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            if (activo) {
                g.setColor(Color.parseColor("#8E95A0"))
                g.setStroke(dp(1), Color.WHITE)
                tv.alpha = 1f
            } else {
                g.setColor(Color.TRANSPARENT)
                tv.alpha = 0.75f
            }
            tv.background = g
        }
    }

    private fun habilitarArrastrePanel(handle: View) {
        handle.setOnTouchListener(object : View.OnTouchListener {
            var x0 = 0
            var y0 = 0
            var tx = 0f
            var ty = 0f
            var arrastrando = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        x0 = pPanel.x
                        y0 = pPanel.y
                        tx = e.rawX
                        ty = e.rawY
                        arrastrando = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (!arrastrando && (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4))) {
                            arrastrando = true
                        }
                        if (arrastrando) {
                            pPanel.x = x0 + dx
                            pPanel.y = y0 + dy
                            wm.updateViewLayout(panel, pPanel)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (arrastrando) {
                            prefs.edit()
                                .putInt("panel_x", pPanel.x)
                                .putInt("panel_y", pPanel.y)
                                .apply()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun centrarPanel() {
        pPanel.x = dp(68)
        pPanel.y = dp(40)
        prefs.edit()
            .putInt("panel_x", pPanel.x)
            .putInt("panel_y", pPanel.y)
            .apply()
        if (panelAbierto) {
            wm.updateViewLayout(panel, pPanel)
        }
        aviso("Panel centrado")
    }

    // ---------- Panel (diseño estilo captura + movimiento libre) ----------

    private fun crearPanel() {
        val dnd = Herramientas.opciones.first { it.id == "dnd" }

        val barraLateral = LinearLayout(this)
        barraLateral.orientation = LinearLayout.VERTICAL
        barraLateral.gravity = Gravity.CENTER_HORIZONTAL
        barraLateral.setPadding(dp(6), dp(12), dp(6), dp(12))
        val fondoRail = GradientDrawable()
        fondoRail.setColor(grisRail)
        fondoRail.cornerRadius = dp(24).toFloat()
        barraLateral.background = fondoRail
        habilitarArrastrePanel(barraLateral)

        val defsPestana = listOf(
            "👁" to 0,
            "🎯" to 1,
            "🔵" to 2,
            "🛠" to 3,
            "⚙" to 4
        )
        for ((emoji, idx) in defsPestana) {
            val btn = TextView(this)
            btn.text = emoji
            btn.textSize = 18f
            btn.gravity = Gravity.CENTER
            btn.setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(dp(42), dp(42))
            lp.bottomMargin = dp(10)
            btn.layoutParams = lp
            btn.setOnClickListener { seleccionarPestana(idx) }
            iconosPestanas.add(btn)
            barraLateral.addView(btn)
        }

        val cab = LinearLayout(this)
        cab.orientation = LinearLayout.HORIZONTAL
        cab.gravity = Gravity.CENTER_VERTICAL
        cab.setPadding(dp(4), dp(2), dp(2), dp(6))
        habilitarArrastrePanel(cab)

        val titulo = TextView(this)
        titulo.text = "☰ FERMXN PANEL"
        titulo.textSize = 14f
        titulo.setTextColor(Color.WHITE)
        titulo.setTypeface(titulo.typeface, Typeface.BOLD)
        cab.addView(titulo, LinearLayout.LayoutParams(0, WRAP, 1f))
        cab.addView(botonCuadro("⌖", Color.WHITE) { centrarPanel() })
        cab.addView(botonCuadro("▼", Color.WHITE) { cerrarPanel() })
        cab.addView(botonCuadro("X", rojo) { stopSelf() })

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
                    aplicarBloqueoMacro(true)
                    aviso("Bolita visible (toque detrás activo)")
                }
                macroBloqueada -> {
                    aplicarBloqueoMacro(false)
                    aviso("Modo mover bolita activo")
                }
                else -> {
                    aplicarBloqueoMacro(true)
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

        seccionMira = LinearLayout(this)
        seccionMira.orientation = LinearLayout.VERTICAL
        seccionMira.visibility = View.VISIBLE

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

        seccionMacro = LinearLayout(this)
        seccionMacro.orientation = LinearLayout.VERTICAL
        seccionMacro.visibility = View.GONE

        val vBloqueo = valorTexto(if (macroBloqueada) "TOQUE DETRÁS 🔒" else "MOVER BOLITA 🔓")
        seccionMacro.addView(fila("Toque y posición", vBloqueo) {
            val nuevoBloqueo = !macroBloqueada
            aplicarBloqueoMacro(nuevoBloqueo)
            vBloqueo.text = if (nuevoBloqueo) "TOQUE DETRÁS 🔒" else "MOVER BOLITA 🔓"
        })

        val vColorMacro = valorTexto(colorMacroActual().nombre + "  ⌄")
        seccionMacro.addView(fila("Color de bolita", vColorMacro) {
            val siguiente = (prefs.getInt("macro_color", 0) + 1) % coloresMacro.size
            prefs.edit().putInt("macro_color", siguiente).apply()
            vColorMacro.text = coloresMacro[siguiente].nombre + "  ⌄"
            aplicarEstiloBolitaMacro()
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

        tabModosHud = LinearLayout(this)
        tabModosHud.orientation = LinearLayout.VERTICAL
        tabModosHud.addView(subtituloSeccion("Rendimiento"))
        tabModosHud.addView(filaChips)
        tabModosHud.addView(filaModos)
        tabModosHud.addView(subtituloSeccion("Pantalla"))
        val checkHud = indicadorCheck(hudActivo)
        tabModosHud.addView(fila("FPS HUD", checkHud) {
            if (!Herramientas.listo()) {
                aviso("Conectá Shizuku primero")
            } else {
                hudActivo = !hudActivo
                if (hudActivo) wm.addView(hud, pHud) else wm.removeView(hud)
                marcar(tileHud, hudActivo, if (hudActivo) "ON" else "OFF")
                val nuevoCheck = indicadorCheck(hudActivo)
                checkHud.text = nuevoCheck.text
                checkHud.setTextColor(nuevoCheck.currentTextColor)
                checkHud.background = nuevoCheck.background
                asegurarLoop()
            }
        })

        tabMira = LinearLayout(this)
        tabMira.orientation = LinearLayout.VERTICAL
        tabMira.addView(subtituloSeccion("Mira"))
        tabMira.addView(filaTiles(tileGuide, tileMiraSet))
        tabMira.addView(seccionMira, paramsFila())

        tabMacro = LinearLayout(this)
        tabMacro.orientation = LinearLayout.VERTICAL
        tabMacro.addView(subtituloSeccion("Bolita Macro"))
        tabMacro.addView(filaTiles(tileMacro, tileBolitaCtrl))
        tabMacro.addView(seccionMacro, paramsFila())

        tabOptimizar = LinearLayout(this)
        tabOptimizar.orientation = LinearLayout.VERTICAL
        tabOptimizar.addView(subtituloSeccion("Sistema"))
        tabOptimizar.addView(filaTiles(tBoost, tCache, tileDnd))
        tabOptimizar.addView(subtituloSeccion("Juego y Red"))
        tabOptimizar.addView(filaTiles(tileTouch, tilePerfil, tilePing, tileHealth))

        tabRapido = LinearLayout(this)
        tabRapido.orientation = LinearLayout.VERTICAL
        tabRapido.addView(subtituloSeccion("Acciones rápidas"))
        tabRapido.addView(filaTiles(tShot, tMusic))
        tabRapido.addView(fila("Centrar panel", valorTexto("⌖")) { centrarPanel() })
        tabRapido.addView(fila("Minimizar panel", valorTexto("▼")) { cerrarPanel() })
        tabRapido.addView(fila("Cerrar servicio", valorTexto("X")) { stopSelf() })

        val contenido = LinearLayout(this)
        contenido.orientation = LinearLayout.VERTICAL
        contenido.addView(tabModosHud)
        contenido.addView(tabMira)
        contenido.addView(tabMacro)
        contenido.addView(tabOptimizar)
        contenido.addView(tabRapido)

        val scroll = ScrollView(this)
        scroll.addView(contenido)

        val areaPrincipal = LinearLayout(this)
        areaPrincipal.orientation = LinearLayout.VERTICAL
        areaPrincipal.setPadding(dp(10), dp(4), dp(4), dp(4))
        areaPrincipal.addView(cab, LinearLayout.LayoutParams(MATCH, WRAP))
        areaPrincipal.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.HORIZONTAL
        raiz.setPadding(dp(8), dp(8), dp(8), dp(8))
        val fondoPanel = GradientDrawable()
        fondoPanel.setColor(grisPanel)
        fondoPanel.cornerRadius = dp(26).toFloat()
        fondoPanel.setStroke(dp(1), bordeSuave)
        raiz.background = fondoPanel
        raiz.addView(barraLateral, LinearLayout.LayoutParams(WRAP, MATCH))
        raiz.addView(areaPrincipal, LinearLayout.LayoutParams(0, MATCH, 1f))
        panel = raiz

        seleccionarPestana(0)

        pPanel = WindowManager.LayoutParams(
            dp(360),
            dp(300),
            tipo,
            banderas,
            PixelFormat.TRANSLUCENT
        )
        pPanel.gravity = Gravity.TOP or Gravity.START
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

        pPanel.width = minOf(dp(560), (dm.widthPixels * 0.92).toInt())
        pPanel.height = minOf(dp(330), (dm.heightPixels * 0.84).toInt())
        pPanel.x = prefs.getInt("panel_x", dp(68))
        pPanel.y = prefs.getInt("panel_y", dp(40))

        wm.addView(panel, pPanel)
        panelAbierto = true
        asegurarLoop()

        ui.post {
            try {
                wm.removeView(burbuja)
                wm.addView(burbuja, pBurbuja)
            } catch (e: Throwable) { }
        }
    }
}
