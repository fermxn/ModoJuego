package com.modojuego.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

class GaugeView(context: Context, private val titulo: String) : View(context) {

    var valor = -1
        set(v) {
            field = v
            invalidate()
        }

    private val pista = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arco = Paint(Paint.ANTI_ALIAS_FLAG)
    private val numero = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rotulo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        pista.style = Paint.Style.STROKE
        pista.strokeCap = Paint.Cap.ROUND
        pista.color = Color.parseColor("#232A3B")
        arco.style = Paint.Style.STROKE
        arco.strokeCap = Paint.Cap.ROUND
        arco.color = Color.parseColor("#E11D48")
        numero.color = Color.WHITE
        numero.textAlign = Paint.Align.CENTER
        numero.typeface = Typeface.DEFAULT_BOLD
        rotulo.color = Color.parseColor("#8A93A6")
        rotulo.textAlign = Paint.Align.CENTER
        rotulo.typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val grosor = 10f * d
        pista.strokeWidth = grosor
        arco.strokeWidth = grosor
        numero.textSize = 26f * d
        rotulo.textSize = 10f * d

        val lado = minOf(width, height).toFloat()
        val margen = grosor / 2f + 4f * d
        val ox = (width - lado) / 2f
        rect.set(ox + margen, margen, ox + lado - margen, lado - margen)

        canvas.drawArc(rect, 135f, 270f, false, pista)
        if (valor >= 0) {
            val v = valor.coerceIn(0, 100)
            canvas.drawArc(rect, 135f, 270f * v / 100f, false, arco)
        }

        val cx = width / 2f
        val cy = height / 2f
        canvas.drawText(if (valor >= 0) "$valor%" else "--", cx, cy + 8f * d, numero)
        canvas.drawText(titulo, cx, cy + 30f * d, rotulo)
    }
}
