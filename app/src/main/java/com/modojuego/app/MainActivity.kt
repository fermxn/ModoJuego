
package com.modojuego.app

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private val ffPackage = "com.dts.freefiremax"
    private lateinit var estado: TextView

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> actualizarEstado() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permListener)

        val p = (24 * resources.displayMetrics.density).toInt()
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(p, p, p, p)
        }

        estado = TextView(this).apply { textSize = 16f; gravity = Gravity.CENTER }
        val bConectar = Button(this).apply {
            text = "Conectar Shizuku"
            setOnClickListener { pedirPermiso() }
        }
        val bJuego = Button(this).apply {
            text = "MODO JUEGO"
            setOnClickListener { modoJuego() }
        }
        val bRestaurar = Button(this).apply {
            text = "Restaurar ajustes"
            setOnClickListener { restaurar() }
        }

        raiz.addView(estado)
        raiz.addView(bConectar)
        raiz.addView(bJuego)
        raiz.addView(bRestaurar)
        setContentView(raiz)
        actualizarEstado()
    }

    override fun onResume() {
        super.onResume()
        actualizarEstado()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    private fun listo(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    private fun actualizarEstado() {
        estado.text = if (listo()) "Shizuku conectado ✔" else "Shizuku no conectado"
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

    private fun ejecutar(cmd: String) {
        Thread {
            try {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                m.isAccessible = true
                val proc = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                proc.waitFor()
            } catch (e: Throwable) {
                runOnUiThread { estado.text = "Error al ejecutar" }
            }
        }.start()
    }

    private fun modoJuego() {
        if (!listo()) {
            estado.text = "Primero tocá Conectar Shizuku"
            return
        }
        ejecutar(
            "am kill-all; " +
                "cmd notification set_dnd priority; " +
                "settings put global window_animation_scale 0.5; " +
                "settings put global transition_animation_scale 0.5; " +
                "settings put global animator_duration_scale 0.5"
        )
        estado.text = "Modo juego activado ✔"
        packageManager.getLaunchIntentForPackage(ffPackage)?.let { startActivity(it) }
    }

    private fun restaurar() {
        if (!listo()) {
            estado.text = "Primero tocá Conectar Shizuku"
            return
        }
        ejecutar(
            "cmd notification set_dnd off; " +
                "settings put global window_animation_scale 1.0; " +
                "settings put global transition_animation_scale 1.0; " +
                "settings put global animator_duration_scale 1.0"
        )
        estado.text = "Ajustes restaurados ✔"
    }
}
