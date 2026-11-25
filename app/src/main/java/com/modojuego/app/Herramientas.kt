package com.modojuego.app

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.Locale

object Herramientas {

    const val FF_MAX = "com.dts.freefiremax"
    const val FF_NORMAL = "com.dts.freefireth"
    const val JUEGO = FF_MAX

    class Opcion(val id: String, val nombre: String, val on: String, val off: String)

    val opciones = listOf(
        Opcion("dnd", "No molestar",
            "cmd notification set_dnd priority", "cmd notification set_dnd off"),
        Opcion("pantalla", "Pantalla siempre encendida",
            "svc power stayon true", "svc power stayon false"),
        Opcion("brillo", "Brillo automático apagado",
            "settings put system screen_brightness_mode 0",
            "settings put system screen_brightness_mode 1"),
        Opcion("bluetooth", "Bluetooth apagado",
            "svc bluetooth disable", "svc bluetooth enable"),
        Opcion("datos", "Ahorro de datos en segundo plano",
            "cmd netpolicy set restrict-background true",
            "cmd netpolicy set restrict-background false")
    )

    class Stats(val fps: Int, val cpu: Int, val ram: String) {
        fun texto(): String {
            val f = if (fps >= 0) "$fps" else "--"
            val c = if (cpu >= 0) "$cpu%" else "--"
            return "FPS: $f\nCPU: $c\n$ram"
        }
    }

    fun listo(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    fun sh(cmd: String): String {
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            val proc = m.invoke(null, arrayOf("sh", "-c", "($cmd) 2>&1"), null, null) as Process
            val salida = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            salida
        } catch (e: Throwable) {
            ""
        }
    }

    private fun prefs(c: Context) =
        c.getSharedPreferences("modojuego", Context.MODE_PRIVATE)

    fun juego(c: Context): String = prefs(c).getString("juego", FF_MAX) ?: FF_MAX

    fun elegirJuego(c: Context, paquete: String) {
        prefs(c).edit().putString("juego", paquete).apply()
    }

    fun activa(c: Context, id: String): Boolean = prefs(c).getBoolean(id, false)

    fun poner(c: Context, o: Opcion, activar: Boolean) {
        prefs(c).edit().putBoolean(o.id, activar).apply()
        sh(if (activar) o.on else o.off)
    }

    fun limpiarRam() {
        sh("am kill-all")
    }

    fun borrarCache() {
        sh("pm trim-caches 999999999999")
    }

    fun restaurarTodo(c: Context) {
        for (o in opciones) {
            if (activa(c, o.id)) poner(c, o, false)
        }
        sh(
            "settings put global window_animation_scale 1.0; " +
                "settings put global transition_animation_scale 1.0; " +
                "settings put global animator_duration_scale 1.0"
        )
    }

    fun ramTexto(c: Context): String {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val libre = mi.availMem / 1048576
        val total = mi.totalMem / 1048576
        return "RAM libre: $libre / $total MB"
    }

    fun ramUso(c: Context): Pair<Int, String> {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val usada = mi.totalMem - mi.availMem
        val pct = (100 * usada / mi.totalMem).toInt()
        val gbUsada = usada / 1073741824.0
        val gbTotal = mi.totalMem / 1073741824.0
        return Pair(pct, String.format(Locale.US, "%.1f / %.1f GB", gbUsada, gbTotal))
    }

    private var cpuTotalAnt = 0L
    private var cpuIdleAnt = 0L

    fun cpuPorcentaje(): Int {
        val linea = sh("head -n 1 /proc/stat").trim()
        val p = linea.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (p.size < 5) return -1
        val idle = p[3] + p[4]
        val total = p.take(8).sum()
        val primera = cpuTotalAnt == 0L
        val dT = total - cpuTotalAnt
        val dI = idle - cpuIdleAnt
        cpuTotalAnt = total
        cpuIdleAnt = idle
        if (primera || dT <= 0) return -1
        return (100 * (dT - dI) / dT).toInt()
    }

    private var capa: String? = null
    private var capaPaquete = ""

    fun fps(paquete: String): Int {
        if (paquete != capaPaquete) {
            capa = null
            capaPaquete = paquete
        }
        if (capa == null) {
            val lista = sh("dumpsys SurfaceFlinger --list").lines()
            capa = lista.firstOrNull { it.contains(paquete) && it.contains("SurfaceView") }
                ?: lista.firstOrNull { it.contains(paquete) }
        }
        val nombre = capa ?: return -1
        val lineas = sh("dumpsys SurfaceFlinger --latency '$nombre'").lines()
        val tiempos = lineas.drop(1).mapNotNull {
            val partes = it.trim().split(Regex("\\s+"))
            if (partes.size == 3) partes[1].toLongOrNull() else null
        }.filter { it > 0L && it != Long.MAX_VALUE }
        val ultimo = tiempos.maxOrNull()
        if (ultimo == null || System.nanoTime() - ultimo > 2_000_000_000L) {
            capa = null
            return -1
        }
        return tiempos.count { it > ultimo - 1_000_000_000L }
    }

    fun estadisticas(c: Context): Stats = Stats(fps(juego(c)), cpuPorcentaje(), ramTexto(c))
}
