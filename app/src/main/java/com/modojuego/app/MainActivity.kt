package com.modojuego.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var estado: TextView
    private lateinit var textoStats: TextView
    private lateinit var bMonitor: Button
    private var monitoreando = false
    private val botonesOpcion = mutableListOf<Pair<Button, Herramientas.Opcion>>()

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> actualizarEstado() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permListener)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 2)
        }

        val p = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(p, p, p, p)

        estado = TextView(this)
        estado.textSize = 16f
        estado.gravity = Gravity.CENTER
        col.addView(estado)

        col.addView(boton("Conectar Shizuku") { pedirPermiso() })
        col.addView(boton("1. Permitir botón flotante") { pedirOverlay() })
        col.addView(boton("2. Mostrar botón flotante") { mostrarFlotante() })
        col.addView(boton("Ocultar botón flotante") {
            stopService(Intent(this, FloatingService::class.java))
        })

        col.addView(boton("MODO JUEGO (limpia RAM + No molestar + abre el juego)") {
            modoJuego()
        })
        col.addView(boton("Limpiar RAM") {
            segundoPlano("RAM limpiada ✔") { Herramientas.limpiarRam() }
        })
        col.addView(boton("Borrar caché") {
            segundoPlano("Caché borrada ✔") { Herramientas.borrarCache() }
        })

        for (o in Herramientas.opciones) {
            val b = boton(etiqueta(o)) { }
            b.setOnClickListener {
                segundoPlano(null) {
                    Herramientas.poner(this, o, !Herramientas.activa(this, o.id))
                    runOnUiThread { b.text = etiqueta(o) }
                }
            }
            botonesOpcion.add(Pair(b, o))
            col.addView(b)
        }

        bMonitor = boton("Ver FPS / CPU / RAM") { alternarMonitor() }
        col.addView(bMonitor)

        textoStats = TextView(this)
        textoStats.textSize = 16f
        textoStats.gravity = Gravity.CENTER
        col.addView(textoStats)

        col.addView(boton("Restaurar todo") { restaurar() })

        val scroll = ScrollView(this)
        scroll.addView(col)
        setContentView(scroll)
        actualizarEstado()
    }

    override fun onResume() {
        super.onResume()
        actualizarEstado()
        for ((b, o) in botonesOpcion) b.text = etiqueta(o)
    }

    override fun onPause() {
        detenerMonitor()
        super.onPause()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    private fun boton(t: String, accion: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener { accion() }
        return b
    }

    private fun etiqueta(o: Herramientas.Opcion): String =
        (if (Herramientas.activa(this, o.id)) "✔ " else "✖ ") + o.nombre

    private fun actualizarEstado() {
        val s = if (Herramientas.listo()) "Shizuku conectado ✔" else "Shizuku no conectado"
        val f = if (Settings.canDrawOverlays(this)) "Botón flotante: permiso OK ✔"
        else "Botón flotante: falta permiso"
        estado.text = s + "\n" + f
    }

    private fun pedirPermiso() {
        try {
            if (!Shizuku.pingBinder()) {
                estado.text = "Abrí Shizuku y activalo primero"
                return
            }
            Shizuku.requestPermission(1)
        } catch (e: Throwable) {
            estado.text = "Abrí Shizuku y activalo primero"
        }
    }

    private fun pedirOverlay() {
        if (Settings.canDrawOverlays(this)) {
            estado.text = "El permiso ya está concedido ✔"
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
            estado.text = "Primero tocá: Permitir botón flotante"
            return
        }
        startForegroundService(Intent(this, FloatingService::class.java))
    }

    private fun segundoPlano(mensaje: String?, accion: () -> Unit) {
        if (!Herramientas.listo()) {
            estado.text = "Primero tocá Conectar Shizuku"
            return
        }
        Thread {
            accion()
            if (mensaje != null) runOnUiThread { estado.text = mensaje }
        }.start()
    }

    private fun modoJuego() {
        if (!Herramientas.listo()) {
            estado.text = "Primero tocá Conectar Shizuku"
            return
        }
        Thread {
            Herramientas.limpiarRam()
            Herramientas.poner(this, Herramientas.opciones.first { it.id == "dnd" }, true)
            runOnUiThread {
                estado.text = "Modo juego activado ✔"
                for ((b, o) in botonesOpcion) b.text = etiqueta(o)
                packageManager.getLaunchIntentForPackage(Herramientas.JUEGO)?.let {
                    startActivity(it)
                }
            }
        }.start()
    }

    private fun restaurar() {
        if (!Herramientas.listo()) {
            estado.text = "Primero tocá Conectar Shizuku"
            return
        }
        Thread {
            Herramientas.restaurarTodo(this)
            runOnUiThread {
                estado.text = "Todo restaurado ✔"
                for ((b, o) in botonesOpcion) b.text = etiqueta(o)
            }
        }.start()
    }

    private fun alternarMonitor() {
        if (monitoreando) {
            detenerMonitor()
            return
        }
        if (!Herramientas.listo()) {
            estado.text = "Primero tocá Conectar Shizuku"
            return
        }
        monitoreando = true
        bMonitor.text = "Detener monitor"
        Thread {
            while (monitoreando) {
                val s = Herramientas.estadisticas(this)
                runOnUiThread { textoStats.text = s.texto() }
                Thread.sleep(1000)
            }
        }.start()
    }

    private fun detenerMonitor() {
        monitoreando = false
        bMonitor.text = "Ver FPS / CPU / RAM"
        textoStats.text = ""
    }
}
