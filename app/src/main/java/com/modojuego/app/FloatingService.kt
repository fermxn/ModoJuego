package com.modojuego.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var burbuja: TextView
    private lateinit var panel: ScrollView
    private lateinit var textoStats: TextView
    private lateinit var mira: MiraView
    private lateinit var pBurbuja: WindowManager.LayoutParams
    private lateinit var pPanel: WindowManager.LayoutParams
    private lateinit var pMira: WindowManager.LayoutParams

    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("modojuego", MODE_PRIVATE) }
    private var creado = false
    private var panelAbierto = false
    private var monitoreando = false
    private var miraVisible = false
    private var hijos = 0

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

    private fun etiquetaMira(): String =
        (if (miraVisible) "✔ " else "✖ ") + "Aim head disimulado"

    private fun etiquetaAim(): String =
        (if (Herramientas.activa(this, opAimlock.id)) "✔ " else "✖ ") + "aimlock"

    private fun textoColor(): String =
        "Color: " + MiraView.COLORES_NOMBRES[
            mira.colorIndice.coerceIn(0, MiraView.COLORES_NOMBRES.size - 1)
        ]

    private fun fondo(color: Int, radio: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radio).toFloat()
        }

    private fun boton(t: String, accion: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.textSize = 13f
        b.setTextColor(Color.WHITE)
        b.minHeight = 0
        b.minimumHeight = 0
        b.background = fondo(Color.parseColor("#FF37474F"), 12)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(40)
        )
        lp.topMargin = dp(4)
        b.layoutParams = lp
        b.setOnClickListener { accion() }
        return b
    }

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

    private fun crearPanel() {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(10), dp(10), dp(10), dp(10))

        val titulo = TextView(this)
        titulo.text = "Modo Juego"
        titulo.setTextColor(Color.WHITE)
        titulo.textSize = 15f
        titulo.gravity = Gravity.CENTER
        col.addView(titulo)

        // ----- Mira -----
        val bMira = boton(etiquetaMira()) { }
        bMira.setOnClickListener {
            mostrarMira(!miraVisible)
            bMira.text = etiquetaMira()
        }
        col.addView(bMira)

        val bDiseno = boton("Diseño: " + MiraView.NOMBRES[mira.diseno]) { }
        bDiseno.setOnClickListener {
            val n = (mira.diseno + 1) % MiraView.NOMBRES.size
            mira.diseno = n
            prefs.edit().putInt("mira_diseno", n).apply()
            bDiseno.text = "Diseño: " + MiraView.NOMBRES[n]
        }
        col.addView(bDiseno)

        val bColor = boton(textoColor()) { }
        bColor.setOnClickListener {
            val n = (mira.colorIndice + 1) % MiraView.COLORES.size
            mira.colorIndice = n
            prefs.edit().putInt("mira_color", n).apply()
            bColor.text = textoColor()
        }
        col.addView(bColor)

        val tamActual = prefs.getInt("mira_tam", 80).coerceIn(24, 1000)
        val etiquetaTam = TextView(this)
        etiquetaTam.text = "Tamaño de la mira: $tamActual"
        etiquetaTam.setTextColor(Color.WHITE)
        etiquetaTam.textSize = 13f
        etiquetaTam.setPadding(0, dp(6), 0, 0)
        col.addView(etiquetaTam)

        val barra = SeekBar(this)
        barra.max = 976
        barra.progress = tamActual - 24
        barra.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progreso: Int, usuario: Boolean) {
                val tam = 24 + progreso
                prefs.edit().putInt("mira_tam", tam).apply()
                etiquetaTam.text = "Tamaño de la mira: $tam"
                aplicarTamanoMira()
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        col.addView(barra)

        // ----- aimlock (sensibilidad táctil) -----
        val bAim = boton(etiquetaAim()) { }
        bAim.setOnClickListener {
            segundoPlano {
                val nuevo = !Herramientas.activa(this, opAimlock.id)
                Herramientas.poner(this, opAimlock, nuevo)
                ui.post { bAim.text = etiquetaAim() }
                aviso(if (nuevo) "aimlock activado ✔" else "aimlock desactivado")
            }
        }
        col.addView(bAim)

        // ----- Herramientas -----
        col.addView(boton("Limpiar RAM") {
            segundoPlano {
                Herramientas.limpiarRam()
                aviso("RAM limpiada ✔")
            }
        })
        col.addView(boton("Borrar caché") {
            segundoPlano {
                Herramientas.borrarCache()
                aviso("Caché borrada ✔")
            }
        })

        // ----- Monitor -----
        val bMon = boton("Mostrar FPS / CPU / RAM") { }
        bMon.setOnClickListener {
            if (monitoreando) {
                detenerMonitor()
                bMon.text = "Mostrar FPS / CPU / RAM"
            } else if (!Herramientas.listo()) {
                aviso("Conectá Shizuku primero")
            } else {
                iniciarMonitor()
                bMon.text = "Ocultar FPS / CPU / RAM"
            }
        }
        col.addView(bMon)

        textoStats = TextView(this)
        textoStats.setTextColor(Color.WHITE)
        textoStats.textSize = 13f
        textoStats.visibility = View.GONE
        col.addView(textoStats)

        col.addView(boton("Cerrar botón flotante") { stopSelf() })

        hijos = col.childCount
        panel = ScrollView(this)
        panel.background = fondo(Color.parseColor("#EE263238"), 16)
        panel.addView(col)

        pPanel = WindowManager.LayoutParams(
            dp(230),
            WindowManager.LayoutParams.WRAP_CONTENT,
            tipo,
            banderas,
            PixelFormat.TRANSLUCENT
        )
        pPanel.gravity = Gravity.TOP or Gravity.START
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

        val maximo = (dm.heightPixels * 0.85).toInt()
        val deseado = hijos * dp(44) + dp(30)
        pPanel.height = minOf(maximo, deseado)

        val ancho = dp(230)
        var px = pBurbuja.x + dp(62)
        if (px + ancho > dm.widthPixels) px = pBurbuja.x - ancho - dp(6)
        pPanel.x = maxOf(px, 0)
        pPanel.y = pBurbuja.y.coerceIn(0, maxOf(0, dm.heightPixels - pPanel.height))

        wm.addView(panel, pPanel)
        panelAbierto = true
    }

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
