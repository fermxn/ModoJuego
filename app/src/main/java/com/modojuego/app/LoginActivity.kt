package com.modojuego.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class LoginActivity : AppCompatActivity() {

    // Huella SHA-256 de la key (la key no está escrita en el código)
    private val huellaKey = "29bfa14f0b5705e40e845310f7188ebfc1c199651efb298582c759473bf7f246"

    private lateinit var campo: EditText
    private lateinit var error: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fondo(color: String, radio: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.parseColor(color))
        g.cornerRadius = dp(radio).toFloat()
        return g
    }

    private fun sha256(t: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(t.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0A0A")
        window.navigationBarColor = Color.parseColor("#0A0A0A")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val raiz = FrameLayout(this)
        raiz.setBackgroundColor(Color.parseColor("#0A0A0A"))
        raiz.addView(
            ParticulasView(this),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val tarjeta = LinearLayout(this)
        tarjeta.orientation = LinearLayout.VERTICAL
        tarjeta.background = fondo("#1A1A1A", 6)
        tarjeta.setPadding(dp(14), dp(14), dp(14), dp(14))

        val titulo = TextView(this)
        titulo.text = "PANEL FERMXN"
        titulo.textSize = 22f
        titulo.setTextColor(Color.WHITE)
        titulo.gravity = Gravity.CENTER
        titulo.setPadding(0, dp(10), 0, dp(18))
        tarjeta.addView(titulo)

        val filaKey = LinearLayout(this)
        filaKey.orientation = LinearLayout.HORIZONTAL
        filaKey.gravity = Gravity.CENTER_VERTICAL
        filaKey.background = fondo("#222222", 6)
        filaKey.setPadding(dp(12), dp(4), dp(12), dp(4))

        val icono = TextView(this)
        icono.text = "🔑"
        icono.textSize = 22f
        filaKey.addView(icono)

        campo = EditText(this)
        campo.hint = "Ingresá tu key"
        campo.setHintTextColor(Color.parseColor("#8A8A8A"))
        campo.setTextColor(Color.WHITE)
        campo.textSize = 17f
        campo.background = null
        campo.setSingleLine(true)
        campo.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        campo.imeOptions = EditorInfo.IME_ACTION_DONE
        campo.setPadding(dp(12), dp(14), dp(4), dp(14))
        campo.setOnEditorActionListener { _, _, _ ->
            verificar()
            true
        }
        filaKey.addView(campo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val lpFila = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        tarjeta.addView(filaKey, lpFila)

        error = TextView(this)
        error.setTextColor(Color.parseColor("#FF5252"))
        error.textSize = 13f
        error.gravity = Gravity.CENTER
        error.setPadding(0, dp(8), 0, 0)
        error.visibility = View.GONE
        tarjeta.addView(error)

        val boton = Button(this)
        boton.text = "ENTRAR"
        boton.isAllCaps = false
        boton.textSize = 16f
        boton.setTextColor(Color.WHITE)
        boton.background = fondo("#1E1E1E", 6)
        boton.setOnClickListener { verificar() }
        val lpBoton = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(54)
        )
        lpBoton.topMargin = dp(10)
        tarjeta.addView(boton, lpBoton)

        val lpTarjeta = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        lpTarjeta.setMargins(dp(20), 0, dp(20), 0)
        raiz.addView(tarjeta, lpTarjeta)

        setContentView(raiz)
    }

    private fun verificar() {
        val ingresada = campo.text.toString().trim()
        if (sha256(ingresada) == huellaKey) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            error.text = "Key incorrecta"
            error.visibility = View.VISIBLE
            campo.setText("")
        }
    }
}
