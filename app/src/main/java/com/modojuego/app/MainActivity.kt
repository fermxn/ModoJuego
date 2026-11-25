package com.modojuego.app

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private val BG = Color.parseColor("#0B0E17")
        private val TARJETA = Color.parseColor("#121826")
        private val PANEL = Color.parseColor("#0E1320")
        private val BORDE = Color.parseColor("#3A1A26")
        private val ROJO = Color.parseColor("#E11D48")
        private val ROJO_OSC = Color.parseColor("#7F1233")
        private val TEXTO = Color.WHITE
        private val GRIS = Color.parseColor("#8A93A6")
        private val VERDE = Color.parseColor("#10B981")
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private lateinit var paginaInicio: View
    private lateinit var paginaHerr: View
    private lateinit var navInicio: TextView
    private lateinit var navHerr: TextView
    private lateinit var vShizuku: TextView
    private lateinit var vFlotante: TextView
    private lateinit var vJuego: TextView
    private lateinit var gCpu: GaugeView
    private lateinit var gRam: GaugeView
    private lateinit var tRamInfo: TextView
    private lateinit var vTermico: TextView
    private lateinit var vHz: TextView
    private lateinit var vFps: TextView
    private lateinit var vAlm: TextView
    private lateinit var bMax: Button
    private lateinit var bNormal: Button
    private val botonesOpcion = mutableListOf<Pair<Button, Herramientas.Opcion>>()

    @Volatile
    private var generacion = 0

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> actualizarEstado() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 2)
        }

        paginaInicio = construirInicio()
        paginaHerr = construirHerramientas()

        val contenido = FrameLayout(this)
        contenido.addView(paginaInicio, FrameLayout.LayoutParams(MATCH, MATCH))
        contenido.addView(paginaHerr, FrameLayout.LayoutParams(MATCH, MATCH))

        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setBackgroundColor(BG)
        raiz.addView(contenido, LinearLayout.LayoutParams(MATCH, 0, 1f))
        raiz.addView(construirNavegacion(), LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(raiz)

        mostrarPagina(true)
        estilarJuego()
        actualizarEstado()
        Shizuku.addRequestPermissionResultListener(permListener)
    }

    override fun onResume() {
        super.onResume()
        actualizarEstado()
        estilarJuego()
        refrescarOpciones()
        generacion++
        val mia = generacion
        Thread {
            while (mia == generacion) {
                val ram = Herramientas.ramUso(this)
                val listo = Herramientas.listo()
                val cpu = if (listo) Herramientas.cpuPorcentaje() else -1
                val fps = if (listo) Herramientas.fps(Herramientas.juego(this)) else -1
                val temp = temperatura()
                val hz = refresco()
                val alm = almacenamiento()
                runOnUiThread {
                    if (mia == generacion) {
                        pintar(cpu, ram.first, ram.second, fps, temp, hz, alm)
                    }
                }
                Thread.sleep(1000)
            }
        }.start()
    }

    override fun onPause() {
        generacion++
        super.onPause()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    // ---------- Utilidades de diseño ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fondo(color: Int, radio: Int, borde: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radio).toFloat()
        if (borde != 0) g.setStroke(dp(1), borde)
        return g
    }

    private fun aviso(t: String) {
        runOnUiThread { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }
    }

    private fun texto(t: String, tam: Float, color: Int, negrita: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = tam
        v.setTextColor(color)
        if (negrita) v.setTypeface(v.typeface, Typeface.BOLD)
        return v
    }

    private fun peso(alto: Int, fin: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(0, alto, 1f)
        lp.marginEnd = dp(fin)
        return lp
    }

    private fun fila(arriba: Int): LinearLayout {
        val f = LinearLayout(this)
        f.orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(arriba)
        f.layoutParams = lp
        return f
    }

    private fun columna(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(14), dp(14), dp(14))
        return c
    }

    private fun tarjeta(): LinearLayout {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.setPadding(dp(16), dp(16), dp(16), dp(16))
        t.background = fondo(TARJETA, 18, BORDE)
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.bottomMargin = dp(12)
        t.layoutParams = lp
        return t
    }

    private fun boton(t: String, relleno: Boolean, accion: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.textSize = 13f
        b.setTextColor(TEXTO)
        b.minHeight = 0
        b.minimumHeight = 0
        pintarBoton(b, relleno)
        val lp = LinearLayout.LayoutParams(MATCH, dp(46))
        lp.topMargin = dp(8)
        b.layoutParams = lp
        b.setOnClickListener { accion() }
        return b
    }

    private fun pintarBoton(b: Button, activo: Boolean) {
        b.background = if (activo) fondo(ROJO_OSC, 12, ROJO) else fondo(PANEL, 12, BORDE)
    }

    private fun chip(titulo: String): Pair<LinearLayout, TextView> {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(10), dp(10), dp(10), dp(10))
        c.background = fondo(PANEL, 12, BORDE)
        c.addView(texto(titulo, 10f, GRIS, true))
        val v = texto("--", 12f, TEXTO, true)
        v.setPadding(0, dp(4), 0, 0)
        c.addView(v)
        return Pair(c, v)
    }

    private fun tile(titulo: String): Pair<LinearLayout, TextView> {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(10), dp(12), dp(10), dp(12))
        c.background = fondo(PANEL, 14, BORDE)
        val etiqueta = texto(titulo, 10f, GRIS, true)
        etiqueta.gravity = Gravity.CENTER
        c.addView(etiqueta)
        val v = texto("--", 18f, ROJO, true)
        v.gravity = Gravity.CENTER
        v.setPadding(0, dp(4), 0, 0)
        c.addView(v)
        return Pair(c, v)
    }

    private fun pildora(t: String): TextView {
        val v = texto(t, 12f, ROJO, true)
        v.gravity = Gravity.CENTER
        v.setPadding(dp(12), dp(6), dp(12), dp(6))
        v.background = fondo(Color.parseColor("#1A0F16"), 10, BORDE)
        return v
    }

    private fun medidor(g: GaugeView, info: TextView): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.setPadding(dp(8), dp(12), dp(8), dp(12))
        c.background = fondo(PANEL, 16, BORDE)
        c.addView(g, LinearLayout.LayoutParams(dp(120), dp(120)))
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.topMargin = dp(8)
        c.addView(info, lp)
        return c
    }

    // ---------- Pantallas ----------

    private fun construirInicio(): ScrollView {
        val col = columna()

        val principal = tarjeta()
        principal.addView(texto("FERMXN THE GOAT GAME V1", 20f, TEXTO, true))
        principal.addView(texto("HUD y optimización para Free Fire", 13f, ROJO))

        val filaChips = fila(14)
        val (c1, v1) = chip("SHIZUKU")
        val (c2, v2) = chip("BOTÓN FLOTANTE")
        val (c3, v3) = chip("JUEGO")
        vShizuku = v1
        vFlotante = v2
        vJuego = v3
        filaChips.addView(c1, peso(WRAP, 6))
        filaChips.addView(c2, peso(WRAP, 6))
        filaChips.addView(c3, peso(WRAP, 0))
        principal.addView(filaChips)

        val filaBotones = fila(10)
        filaBotones.addView(boton("CONECTAR SHIZUKU", false) { pedirPermiso() }, peso(dp(46), 6))
        filaBotones.addView(boton("MODO JUEGO 🚀", true) { modoJuego() }, peso(dp(46), 0))
        principal.addView(filaBotones)
        col.addView(principal)

        val juego = tarjeta()
        juego.addView(texto("Juego a optimizar", 15f, TEXTO, true))
        juego.addView(texto("Elegí qué versión de Free Fire usás", 12f, GRIS))
        val filaJuego = fila(10)
        bMax = boton("Free Fire MAX", false) { elegirJuego(Herramientas.FF_MAX) }
        bNormal = boton("Free Fire", false) { elegirJuego(Herramientas.FF_NORMAL) }
        filaJuego.addView(bMax, peso(dp(46), 6))
        filaJuego.addView(bNormal, peso(dp(46), 0))
        juego.addView(filaJuego)
        col.addView(juego)

        val tele = tarjeta()
        tele.addView(texto("Telemetría en tiempo real", 16f, TEXTO, true))
        tele.addView(texto("CPU, RAM, térmico, pantalla y FPS del juego", 12f, GRIS))

        val filaMed = fila(12)
        gCpu = GaugeView(this, "USO DE CPU")
        gRam = GaugeView(this, "USO DE RAM")
        tRamInfo = pildora("-- / -- GB")
        val nucleos = Runtime.getRuntime().availableProcessors()
        filaMed.addView(medidor(gCpu, pildora("$nucleos núcleos")), peso(WRAP, 6))
        filaMed.addView(medidor(gRam, tRamInfo), peso(WRAP, 0))
        tele.addView(filaMed)

        val fila1 = fila(10)
        val (t1, vt) = tile("TÉRMICO")
        val (t2, vh) = tile("PANTALLA")
        vTermico = vt
        vHz = vh
        fila1.addView(t1, peso(WRAP, 6))
        fila1.addView(t2, peso(WRAP, 0))
        tele.addView(fila1)

        val fila2 = fila(10)
        val (t3, vf) = tile("FPS DEL JUEGO")
        val (t4, va) = tile("ALMACENAMIENTO")
        vFps = vf
        vAlm = va
        fila2.addView(t3, peso(WRAP, 6))
        fila2.addView(t4, peso(WRAP, 0))
        tele.addView(fila2)

        val nota = texto(
            "CPU y FPS necesitan Shizuku conectado. Los FPS se miden con el juego abierto.",
            11f,
            GRIS
        )
        nota.setPadding(0, dp(10), 0, 0)
        tele.addView(nota)
        col.addView(tele)

        val sv = ScrollView(this)
        sv.addView(col)
        return sv
    }

    private fun construirHerramientas(): ScrollView {
        val col = columna()
        col.addView(texto("Herramientas", 20f, TEXTO, true))
        val sub = texto("Todo lo activás vos manualmente", 13f, GRIS)
        sub.setPadding(0, 0, 0, dp(12))
        col.addView(sub)

        val flotante = tarjeta()
        flotante.addView(texto("Botón flotante", 15f, TEXTO, true))
        flotante.addView(boton("1. Permitir botón flotante", false) { pedirOverlay() })
        flotante.addView(boton("2. Mostrar botón flotante", true) { mostrarFlotante() })
        flotante.addView(boton("Ocultar botón flotante", false) {
            stopService(Intent(this, FloatingService::class.java))
        })
        col.addView(flotante)

        val rendimiento = tarjeta()
        rendimiento.addView(texto("Rendimiento", 15f, TEXTO, true))
        rendimiento.addView(boton("Limpiar RAM", false) {
            segundoPlano("RAM limpiada ✔") { Herramientas.limpiarRam() }
        })
        rendimiento.addView(boton("Borrar caché", false) {
            segundoPlano("Caché borrada ✔") { Herramientas.borrarCache() }
        })
        for (o in Herramientas.opciones) {
            val b = boton(etiqueta(o), false) { }
            b.setOnClickListener {
                segundoPlano(null) {
                    Herramientas.poner(this, o, !Herramientas.activa(this, o.id))
                    runOnUiThread { refrescarOpciones() }
                }
            }
            botonesOpcion.add(Pair(b, o))
            rendimiento.addView(b)
        }
        rendimiento.addView(boton("Restaurar todo", false) { restaurar() })
        col.addView(rendimiento)

        val sv = ScrollView(this)
        sv.addView(col)
        return sv
    }

    private fun itemNav(t: String, accion: () -> Unit): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 14f
        v.gravity = Gravity.CENTER
        v.setPadding(0, dp(12), 0, dp(12))
        v.setOnClickListener { accion() }
        return v
    }

    private fun construirNavegacion(): LinearLayout {
        val barra = LinearLayout(this)
        barra.orientation = LinearLayout.HORIZONTAL
        barra.setPadding(dp(12), dp(8), dp(12), dp(8))
        barra.setBackgroundColor(TARJETA)
        navInicio = itemNav("Inicio") { mostrarPagina(true) }
        navHerr = itemNav("Herramientas") { mostrarPagina(false) }
        barra.addView(navInicio, peso(WRAP, 8))
        barra.addView(navHerr, peso(WRAP, 0))
        return barra
    }

    private fun mostrarPagina(inicio: Boolean) {
        paginaInicio.visibility = if (inicio) View.VISIBLE else View.GONE
        paginaHerr.visibility = if (inicio) View.GONE else View.VISIBLE
        navInicio.setTextColor(if (inicio) TEXTO else GRIS)
        navHerr.setTextColor(if (inicio) GRIS else TEXTO)
        navInicio.background = if (inicio) fondo(ROJO_OSC, 14, ROJO) else null
        navHerr.background = if (inicio) null else fondo(ROJO_OSC, 14, ROJO)
    }

    // ---------- Estado y datos ----------

    private fun etiqueta(o: Herramientas.Opcion): String =
        (if (Herramientas.activa(this, o.id)) "✔ " else "✖ ") + o.nombre

    private fun refrescarOpciones() {
        for ((b, o) in botonesOpcion) {
            b.text = etiqueta(o)
            pintarBoton(b, Herramientas.activa(this, o.id))
        }
    }

    private fun estilarJuego() {
        val pkg = Herramientas.juego(this)
        pintarBoton(bMax, pkg == Herramientas.FF_MAX)
        pintarBoton(bNormal, pkg == Herramientas.FF_NORMAL)
    }

    private fun actualizarEstado() {
        val ok = Herramientas.listo()
        vShizuku.text = if (ok) "EN LÍNEA" else "DESCONECTADO"
        vShizuku.setTextColor(if (ok) VERDE else ROJO)

        val ov = Settings.canDrawOverlays(this)
        vFlotante.text = if (ov) "PERMISO OK" else "FALTA PERMISO"
        vFlotante.setTextColor(if (ov) VERDE else ROJO)

        val pkg = Herramientas.juego(this)
        val instalado = packageManager.getLaunchIntentForPackage(pkg) != null
        val nombre = if (pkg == Herramientas.FF_MAX) "MAX" else "NORMAL"
        vJuego.text = if (instalado) nombre else "NO INSTALADO"
        vJuego.setTextColor(if (instalado) VERDE else ROJO)
    }

    private fun pintar(
        cpu: Int,
        ramPct: Int,
        ramTxt: String,
        fps: Int,
        temp: String,
        hz: String,
        alm: String
    ) {
        gCpu.valor = cpu
        gRam.valor = ramPct
        tRamInfo.text = ramTxt
        vFps.text = if (fps >= 0) "$fps" else "--"
        vTermico.text = temp
        vHz.text = hz
        vAlm.text = alm
    }

    private fun temperatura(): String {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "--"
        val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        return if (t > 0) String.format(Locale.US, "%.1f°C", t / 10f) else "--"
    }

    @Suppress("DEPRECATION")
    private fun refresco(): String {
        val hz = windowManager.defaultDisplay.refreshRate
        return String.format(Locale.US, "%.0f Hz", hz)
    }

    private fun almacenamiento(): String {
        return try {
            val s = StatFs(Environment.getDataDirectory().path)
            val gb = s.availableBytes / 1_000_000_000.0
            String.format(Locale.US, "%.0f GB", gb)
        } catch (e: Throwable) {
            "--"
        }
    }

    // ---------- Acciones ----------

    private fun elegirJuego(paquete: String) {
        Herramientas.elegirJuego(this, paquete)
        estilarJuego()
        actualizarEstado()
    }

    private fun pedirPermiso() {
        try {
            if (!Shizuku.pingBinder()) {
                aviso("Abrí Shizuku y activalo primero")
                return
            }
            Shizuku.requestPermission(1)
        } catch (e: Throwable) {
            aviso("Abrí Shizuku y activalo primero")
        }
    }

    private fun pedirOverlay() {
        if (Settings.canDrawOverlays(this)) {
            aviso("El permiso ya está concedido ✔")
            return
        }
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun mostrarFlotante() {
        if (!Settings.canDrawOverlays(this)) {
            aviso("Primero tocá: Permitir botón flotante")
            return
        }
        startForegroundService(Intent(this, FloatingService::class.java))
    }

    private fun segundoPlano(mensaje: String?, accion: () -> Unit) {
        if (!Herramientas.listo()) {
            aviso("Primero tocá Conectar Shizuku")
            return
        }
        Thread {
            accion()
            if (mensaje != null) aviso(mensaje)
        }.start()
    }

    private fun modoJuego() {
        if (!Herramientas.listo()) {
            aviso("Primero tocá Conectar Shizuku")
            return
        }
        Thread {
            Herramientas.limpiarRam()
            Herramientas.poner(this, Herramientas.opciones.first { it.id == "dnd" }, true)
            runOnUiThread {
                refrescarOpciones()
                val intento = packageManager.getLaunchIntentForPackage(Herramientas.juego(this))
                if (intento != null) {
                    aviso("Modo juego activado ✔")
                    startActivity(intento)
                } else {
                    aviso("Free Fire no está instalado")
                }
            }
        }.start()
    }

    private fun restaurar() {
        if (!Herramientas.listo()) {
            aviso("Primero tocá Conectar Shizuku")
            return
        }
        Thread {
            Herramientas.restaurarTodo(this)
            runOnUiThread {
                refrescarOpciones()
                aviso("Todo restaurado ✔")
            }
        }.start()
    }
}
