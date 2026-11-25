package com.modojuego.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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

    private lateinit var wm: WindowManager
    private lateinit var burbuja: TextView
    private lateinit var panel: LinearLayout
    private lateinit var panelFondo: GradientDrawable
    private lateinit var scrollContenido: ScrollView
    private lateinit var tituloTab: TextView
    private lateinit var bLuna: TextView
    private lateinit var bSol: TextView
    private lateinit var textoStats: TextView
    private lateinit var mira: MiraView
    private lateinit var pBurbuja: WindowManager.LayoutParams
    private lateinit var pPanel: WindowManager.LayoutParams
    private lateinit var pMira: WindowManager.LayoutParams

    private val tabs = mutableListOf<TextView>()
    private val paginas = mutableListOf<LinearLayout>()
    private val titulos = listOf("Monitor", "Mira", "Herramientas", "Ajustes")
    private val nombresSens = listOf("Normal", "Medio alto", "Alto")

    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("modojuego", MODE_PRIVATE) }
    private var creado = false
    private var panelAbierto = false
    private var monitoreando = false
    private var miraVisible = false
    private var tema = 0

    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    private val azul = Color.parseColor("#8EA0F5")
    private val azulSuave = Color.parseColor("#B38EA0F5")

    private val opAimlock = Herramientas.Opcion(
        "aimlock",
        "aimlock",
        "settings put secure long_press_timeout 250; " +
            "settings put system high_touch_sensitivity_enable 1",
        "settings put secure long_press_timeout 400; " +
            "settings put system high_touch_sensitivity_enable 0"
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
            crearPanel()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        monitoreando = false
        try { if (panelAbierto) wm.removeView(panel) } catch (e: Throwable) { }
        try { if (miraVisible) wm.removeView(mira) } catch (e: Throwable) { }
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

    // ---------- Sensibilidad de la pantalla (frecuencia de refresco) ----------

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

    private fun aplicarSensibilidad(nivel: Int) {
        segundoPlano {
            prefs.edit().putInt("sens_nivel", nivel).apply()
            val maxHz = hzMaximo()
            val max = String.format(Locale.US, "%.1f", maxHz)
            val comando = when (nivel) {
                0 -> "settings delete system min_refresh_rate; " +
                    "settings delete system peak_refresh_rate"
                1 -> "settings put system min_refresh_rate 60.0; " +
                    "settings put system peak_refresh_rate $max"
                else -> "settings put system min_refresh_rate $max; " +
                    "settings put system peak_refresh_rate $max"
            }
            Herramientas.sh(comando)
            Thread.sleep(1500)
            val ahora = hzActual()
            val ahoraTxt = String.format(Locale.US, "%.0f Hz", ahora)
            val maxTxt = String.format(Locale.US, "%.0f Hz", maxHz)
            val nombre = nombresSens[nivel]
            if (nivel == 2 && ahora < maxHz - 1f) {
                aviso("Tu celular ignoró el ajuste: la pantalla sigue en $ahoraTxt")
            } else if (nivel == 0) {
                aviso("Sensibilidad $nombre ✔ (automático, ahora $ahoraTxt)")
            } else if (nivel == 1) {
                aviso("Sensibilidad $nombre ✔ (hasta $maxTxt, ahora $ahoraTxt)")
            } else {
                aviso("Sensibilidad $nombre ✔ (pantalla fija en $ahoraTxt)")
            }
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
        circulo.setColor(Color.parseColor("#CC1E88E5"))
        burbuja.background = circulo

        val tam = dp(56)
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
                            if (panelAbierto) alternarPanel()
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

    // ---------- Piezas de diseño ----------

    private fun pildora(radio: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.parseColor("#26FFFFFF"))
        g.cornerRadius = dp(radio).toFloat()
        g.setStroke(dp(1), Color.parseColor("#80FFFFFF"))
        return g
    }

    private fun circuloFondo(activo: Boolean): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(if (activo) azulSuave else Color.parseColor("#26FFFFFF"))
        g.setStroke(dp(1), Color.parseColor("#80FFFFFF"))
        return g
    }

    private fun botonCirculo(t: String, alTocar: () -> Unit): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 16f
        v.gravity = Gravity.CENTER
        v.setTextColor(Color.WHITE)
        v.setOnClickListener { alTocar() }
        val lp = LinearLayout.LayoutParams(dp(36), dp(36))
        lp.marginStart = dp(8)
        v.layoutParams = lp
        return v
    }

    private fun indicador(activo: Boolean): TextView {
        val t = TextView(this)
        t.gravity = Gravity.CENTER
        t.textSize = 14f
        t.setTextColor(Color.WHITE)
        t.layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        pintarIndicador(t, activo)
        return t
    }

    private fun pintarIndicador(t: TextView, activo: Boolean) {
        t.text = if (activo) "✓" else ""
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        if (activo) {
            g.setColor(azul)
        } else {
            g.setColor(Color.TRANSPARENT)
            g.setStroke(dp(1), Color.parseColor("#B3FFFFFF"))
        }
        t.background = g
    }

    private fun valorTexto(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 14f
        v.setTextColor(Color.WHITE)
        return v
    }

    private fun paramsFila(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(8)
        return lp
    }

    private fun fila(texto: String, derecha: View, alTocar: () -> Unit): LinearLayout {
        val f = LinearLayout(this)
        f.orientation = LinearLayout.HORIZONTAL
        f.gravity = Gravity.CENTER_VERTICAL
        f.setPadding(dp(16), dp(10), dp(12), dp(10))
        f.background = pildora(22)
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
        c.background = pildora(22)

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
        barra.progressTintList = ColorStateList.valueOf(azul)
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

    private fun tarjetaNiveles(
        titulo: String,
        nombres: List<String>,
        inicial: Int,
        pista: String,
        alSoltar: (Int) -> Unit
    ): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(12), dp(16), dp(10))
        c.background = pildora(22)

        val arriba = LinearLayout(this)
        arriba.orientation = LinearLayout.HORIZONTAL
        val t = TextView(this)
        t.text = titulo
        t.setTextColor(Color.WHITE)
        t.textSize = 15f
        arriba.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        val v = TextView(this)
        v.text = nombres[inicial]
        v.setTextColor(Color.WHITE)
        v.textSize = 15f
        arriba.addView(v)
        c.addView(arriba)

        val barra = SeekBar(this)
        barra.max = nombres.size - 1
        barra.progress = inicial
        barra.progressTintList = ColorStateList.valueOf(azul)
        barra.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        barra.progressBackgroundTintList =
            ColorStateList.valueOf(Color.parseColor("#66FFFFFF"))
        barra.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progreso: Int, usuario: Boolean) {
                v.text = nombres[progreso]
            }

            override fun onStartTrackingTouch(s: SeekBar) {}

            override fun onStopTrackingTouch(s: SeekBar) {
                alSoltar(s.progress)
            }
        })
        c.addView(barra)

        val p = TextView(this)
        p.text = pista
        p.setTextColor(Color.parseColor("#CCFFFFFF"))
        p.textSize = 12f
        c.addView(p)

        c.layoutParams = paramsFila()
        return c
    }

    private fun pagina(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(0, 0, 0, dp(8))
        return p
    }

    // ---------- Panel ----------

    private fun crearPanel() {
        tema = prefs.getInt("panel_tema", 0)

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.HORIZONTAL
        raiz.setPadding(dp(8), dp(8), dp(8), dp(8))
        panelFondo = GradientDrawable()
        panelFondo.cornerRadius = dp(28).toFloat()
        panelFondo.setStroke(dp(1), Color.parseColor("#66FFFFFF"))
        raiz.background = panelFondo

        // Barra lateral con íconos
        val lateral = LinearLayout(this)
        lateral.orientation = LinearLayout.VERTICAL
        lateral.gravity = Gravity.CENTER_HORIZONTAL
        lateral.setPadding(dp(6), dp(6), dp(6), dp(6))
        lateral.background = pildora(28)
        val iconos = listOf("👁", "🎯", "🛠", "⚙")
        for (i in iconos.indices) {
            val t = TextView(this)
            t.text = iconos[i]
            t.textSize = 20f
            t.gravity = Gravity.CENTER
            t.setTextColor(Color.WHITE)
            t.setOnClickListener { mostrarTab(i) }
            val lp = LinearLayout.LayoutParams(dp(44), dp(44))
            lp.topMargin = dp(4)
            lp.bottomMargin = dp(4)
            lateral.addView(t, lp)
            tabs.add(t)
        }
        raiz.addView(lateral, LinearLayout.LayoutParams(dp(56), MATCH))

        // Columna derecha: cabecera + contenido
        val derecha = LinearLayout(this)
        derecha.orientation = LinearLayout.VERTICAL

        val cab = LinearLayout(this)
        cab.orientation = LinearLayout.HORIZONTAL
        cab.gravity = Gravity.CENTER_VERTICAL

        tituloTab = TextView(this)
        tituloTab.setTextColor(Color.WHITE)
        tituloTab.textSize = 15f
        tituloTab.setPadding(dp(16), dp(8), dp(16), dp(8))
        tituloTab.background = pildora(22)
        cab.addView(tituloTab, LinearLayout.LayoutParams(0, WRAP, 1f))

        bLuna = botonCirculo("🌙") {
            tema = 0
            prefs.edit().putInt("panel_tema", 0).apply()
            aplicarTema()
        }
        bSol = botonCirculo("☀") {
            tema = 1
            prefs.edit().putInt("panel_tema", 1).apply()
            aplicarTema()
        }
        cab.addView(bLuna)
        cab.addView(bSol)
        derecha.addView(cab, LinearLayout.LayoutParams(MATCH, WRAP))

        scrollContenido = ScrollView(this)
        val lpScroll = LinearLayout.LayoutParams(MATCH, 0, 1f)
        derecha.addView(scrollContenido, lpScroll)

        val lpDerecha = LinearLayout.LayoutParams(0, MATCH, 1f)
        lpDerecha.marginStart = dp(8)
        raiz.addView(derecha, lpDerecha)

        // ----- Página 0: Monitor -----
        val pgMonitor = pagina()
        val indMon = indicador(false)
        pgMonitor.addView(fila("Mostrar FPS / CPU / RAM", indMon) {
            if (monitoreando) {
                detenerMonitor()
                pintarIndicador(indMon, false)
            } else if (!Herramientas.listo()) {
                aviso("Conectá Shizuku primero")
            } else {
                iniciarMonitor()
                pintarIndicador(indMon, true)
            }
        })
        textoStats = TextView(this)
        textoStats.setTextColor(Color.WHITE)
        textoStats.textSize = 14f
        textoStats.setPadding(dp(16), dp(12), dp(16), dp(12))
        textoStats.background = pildora(18)
        textoStats.visibility = View.GONE
        pgMonitor.addView(textoStats, paramsFila())
        paginas.add(pgMonitor)

        // ----- Página 1: Mira -----
        val pgMira = pagina()
        val indMira = indicador(false)
        pgMira.addView(fila("Aim head disimulado", indMira) {
            mostrarMira(!miraVisible)
            pintarIndicador(indMira, miraVisible)
        })
        val vDiseno = valorTexto(MiraView.NOMBRES[mira.diseno] + "  ⌄")
        pgMira.addView(fila("Diseño", vDiseno) {
            val n = (mira.diseno + 1) % MiraView.NOMBRES.size
            mira.diseno = n
            prefs.edit().putInt("mira_diseno", n).apply()
            vDiseno.text = MiraView.NOMBRES[n] + "  ⌄"
        })
        val vColor = valorTexto(nombreColor() + "  ⌄")
        pgMira.addView(fila("Color", vColor) {
            val n = (mira.colorIndice + 1) % MiraView.COLORES.size
            mira.colorIndice = n
            prefs.edit().putInt("mira_color", n).apply()
            vColor.text = nombreColor() + "  ⌄"
        })
        val tamActual = prefs.getInt("mira_tam", 80).coerceIn(24, 1000)
        pgMira.addView(tarjetaSlider("Tamaño de la mira", tamActual, 24, 1000) { v ->
            prefs.edit().putInt("mira_tam", v).apply()
            aplicarTamanoMira()
        })
        paginas.add(pgMira)

        // ----- Página 2: Herramientas -----
        val pgHerr = pagina()
        val nivelSens = prefs.getInt("sens_nivel", 0).coerceIn(0, 2)
        pgHerr.addView(
            tarjetaNiveles(
                "Sensibilidad de la pantalla",
                nombresSens,
                nivelSens,
                "Sube la frecuencia de pantalla para una respuesta táctil más rápida. " +
                    "Al soltar te muestro los Hz reales."
            ) { n -> aplicarSensibilidad(n) }
        )
        val indAim = indicador(Herramientas.activa(this, opAimlock.id))
        pgHerr.addView(fila("aimlock", indAim) {
            segundoPlano {
                val nuevo = !Herramientas.activa(this, opAimlock.id)
                Herramientas.poner(this, opAimlock, nuevo)
                ui.post { pintarIndicador(indAim, nuevo) }
                aviso(if (nuevo) "aimlock activado ✔" else "aimlock desactivado")
            }
        })
        pgHerr.addView(fila("Limpiar RAM", valorTexto("›")) {
            segundoPlano {
                Herramientas.limpiarRam()
                aviso("RAM limpiada ✔")
            }
        })
        pgHerr.addView(fila("Borrar caché", valorTexto("›")) {
            segundoPlano {
                Herramientas.borrarCache()
                aviso("Caché borrada ✔")
            }
        })
        paginas.add(pgHerr)

        // ----- Página 3: Ajustes -----
        val pgAjustes = pagina()
        pgAjustes.addView(fila("Cerrar botón flotante", valorTexto("✕")) { stopSelf() })
        paginas.add(pgAjustes)

        panel = raiz
        pPanel = WindowManager.LayoutParams(
            dp(380),
            dp(260),
            tipo,
            banderas,
            PixelFormat.TRANSLUCENT
        )
        pPanel.gravity = Gravity.TOP or Gravity.START

        aplicarTema()
        mostrarTab(0)
    }

    private fun aplicarTema() {
        panelFondo.setColor(Color.parseColor(if (tema == 0) "#E6232A36" else "#D98A93A3"))
        bLuna.background = circuloFondo(tema == 0)
        bSol.background = circuloFondo(tema == 1)
    }

    private fun mostrarTab(i: Int) {
        for (k in tabs.indices) {
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(if (k == i) azulSuave else Color.TRANSPARENT)
            tabs[k].background = g
        }
        tituloTab.text = titulos[i]
        scrollContenido.removeAllViews()
        scrollContenido.addView(paginas[i])
    }

    private fun alternarPanel() {
        if (panelAbierto) {
            wm.removeView(panel)
            panelAbierto = false
            return
        }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)

        val ancho = minOf(dp(380), (dm.widthPixels * 0.92).toInt())
        val alto = minOf(dp(260), (dm.heightPixels * 0.9).toInt())
        pPanel.width = ancho
        pPanel.height = alto

        var px = pBurbuja.x + dp(62)
        if (px + ancho > dm.widthPixels) px = pBurbuja.x - ancho - dp(6)
        pPanel.x = maxOf(px, 0)
        pPanel.y = pBurbuja.y.coerceIn(0, maxOf(0, dm.heightPixels - alto))

        wm.addView(panel, pPanel)
        panelAbierto = true

        // La burbuja queda siempre por encima del panel
        ui.post {
            try {
                wm.removeView(burbuja)
                wm.addView(burbuja, pBurbuja)
            } catch (e: Throwable) { }
        }
    }

    // ---------- Monitor ----------

    private fun iniciarMonitor() {
        if (monitoreando) return
        monitoreando = true
        textoStats.visibility = View.VISIBLE
        burbuja.textSize = 12f
        Thread {
            while (monitoreando) {
                val s = Herramientas.estadisticas(this)
                ui.post {
                    textoStats.text = s.texto()
                    burbuja.text = if (s.fps >= 0) "${s.fps}\nFPS" else "--\nFPS"
                }
                Thread.sleep(1000)
            }
        }.start()
    }

    private fun detenerMonitor() {
        monitoreando = false
        textoStats.visibility = View.GONE
        burbuja.textSize = 22f
        burbuja.text = "⚡"
    }
}
