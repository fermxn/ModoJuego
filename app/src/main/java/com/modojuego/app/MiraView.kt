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

        val COLORES_NOMBRES = listOf(
            "Rojo",
            "Verde",
            "Blanco",
            "Amarillo",
            "Cian",
            "Magenta",
            "Naranja"
        )

        val COLORES = listOf(
            Color.parseColor("#FFFF1744"),
            Color.parseColor("#FF00E676"),
            Color.parseColor("#FFFFFFFF"),
            Color.parseColor("#FFFFEA00"),
            Color.parseColor("#FF00E5FF"),
            Color.parseColor("#FFE040FB"),
            Color.parseColor("#FFFF9100")
        )
    }

    var diseno = 0
        set(valor) {
            field = valor
            invalidate()
        }

    var colorIndice = 0
        set(valor) {
            field = valor
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun colorMira(): Int =
        COLORES[colorIndice.coerceIn(0, COLORES.size - 1)]

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val cx = w / 2f
        val cy = height / 2f
        val r = w / 2f - 4f
        val g = (w / 40f).coerceIn(2f, 5f * d)
        val rPunto = (w / 14f).coerceIn(3f, 9f * d)
        val rPuntoChico = (w / 16f).coerceIn(3f, 8f * d)

        when (diseno) {
            0 -> punto(canvas, cx, cy, rPunto)
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
                punto(canvas, cx, cy, rPuntoChico)
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
        p.color = colorMira()
        p.strokeWidth = g
        c.drawLine(x1, y1, x2, y2, p)
    }

    private fun circulo(c: Canvas, cx: Float, cy: Float, r: Float, g: Float) {
        p.style = Paint.Style.STROKE
        p.color = Color.BLACK
        p.strokeWidth = g + 3f
        c.drawCircle(cx, cy, r, p)
        p.color = colorMira()
        p.strokeWidth = g
        c.drawCircle(cx, cy, r, p)
    }

    private fun punto(c: Canvas, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        c.drawCircle(cx, cy, r + 1.5f, p)
        p.color = colorMira()
        c.drawCircle(cx, cy, r, p)
    }
}
