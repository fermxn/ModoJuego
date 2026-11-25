package com.modojuego.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class MiraView(context: Context) : View(context) {

    companion object {
        val NOMBRES = listOf(
            "Punto",
            "Cruz",
            "Cruz con hueco",
            "Círculo",
            "Círculo con punto",
            "Mira en T"
        )
    }

    var diseno = 0
        set(valor) {
            field = valor
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rojo = Color.parseColor("#FFFF1744")

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val cx = w / 2f
        val cy = height / 2f
        val r = w / 2f - 4f
        val g = maxOf(2f, w / 40f)

        when (diseno) {
            0 -> punto(canvas, cx, cy, maxOf(3f, w / 14f))
            1 -> {
                linea(canvas, cx - r, cy, cx + r, cy, g)
                linea(canvas, cx, cy - r, cx, cy + r, g)
            }
            2 -> {
                val h = r * 0.25f
                linea(canvas, cx - r, cy, cx - h, cy, g)
                linea(canvas, cx + h, cy, cx + r, cy, g)
                linea(canvas, cx, cy - r, cx, cy - h, g)
                linea(canvas, cx, cy + h, cx, cy + r, g)
            }
            3 -> circulo(canvas, cx, cy, r, g)
            4 -> {
                circulo(canvas, cx, cy, r, g)
                punto(canvas, cx, cy, maxOf(3f, w / 16f))
            }
            5 -> {
                linea(canvas, cx - r, cy, cx + r, cy, g)
                linea(canvas, cx, cy, cx, cy + r, g)
            }
        }
    }

    private fun linea(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, g: Float) {
        p.style = Paint.Style.STROKE
        p.color = Color.BLACK
        p.strokeWidth = g + 3f
        c.drawLine(x1, y1, x2, y2, p)
        p.color = rojo
        p.strokeWidth = g
        c.drawLine(x1, y1, x2, y2, p)
    }

    private fun circulo(c: Canvas, cx: Float, cy: Float, r: Float, g: Float) {
        p.style = Paint.Style.STROKE
        p.color = Color.BLACK
        p.strokeWidth = g + 3f
        c.drawCircle(cx, cy, r, p)
        p.color = rojo
        p.strokeWidth = g
        c.drawCircle(cx, cy, r, p)
    }

    private fun punto(c: Canvas, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        c.drawCircle(cx, cy, r + 1.5f, p)
        p.color = rojo
        c.drawCircle(cx, cy, r, p)
    }
}
