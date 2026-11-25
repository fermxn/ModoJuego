package com.modojuego.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.random.Random

class ParticulasView(context: Context) : View(context) {

    private class Punto(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val r: Float
    )

    private val puntos = mutableListOf<Punto>()
    private val pPunto = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pLinea = Paint(Paint.ANTI_ALIAS_FLAG)
    private var ultimo = 0L

    init {
        pPunto.color = Color.parseColor("#FF1A1A")
        pLinea.color = Color.WHITE
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        puntos.clear()
        val d = resources.displayMetrics.density
        repeat(38) {
            puntos.add(
                Punto(
                    Random.nextFloat() * w,
                    Random.nextFloat() * h,
                    (Random.nextFloat() - 0.5f) * 40f * d,
                    (Random.nextFloat() - 0.5f) * 40f * d,
                    (1.5f + Random.nextFloat() * 2.5f) * d
                )
            )
        }
        ultimo = 0L
    }

    override fun onDraw(canvas: Canvas) {
        val ahora = System.nanoTime()
        var dt = if (ultimo == 0L) 0f else (ahora - ultimo) / 1_000_000_000f
        if (dt > 0.05f) dt = 0.05f
        ultimo = ahora

        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density

        for (p in puntos) {
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.x < 0f || p.x > w) p.vx = -p.vx
            if (p.y < 0f || p.y > h) p.vy = -p.vy
        }

        val maxDist = 110f * d
        pLinea.strokeWidth = d
        for (i in puntos.indices) {
            for (j in i + 1 until puntos.size) {
                val a = puntos[i]
                val b = puntos[j]
                val dx = a.x - b.x
                val dy = a.y - b.y
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (dist < maxDist) {
                    pLinea.alpha = (150 * (1f - dist / maxDist)).toInt()
                    canvas.drawLine(a.x, a.y, b.x, b.y, pLinea)
                }
            }
        }

        for (p in puntos) {
            canvas.drawCircle(p.x, p.y, p.r, pPunto)
        }

        postInvalidateOnAnimation()
    }
}
