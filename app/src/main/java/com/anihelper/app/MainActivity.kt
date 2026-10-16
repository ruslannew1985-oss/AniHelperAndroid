package com.anihelper.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 80, 50, 50)
        }

        val title = TextView(this).apply {
            text = "Ани ✨"
            textSize = 32f
        }

        val description = TextView(this).apply {
            text = "Твоя аниме-помощница\n\nНажми кнопку, чтобы Ани появилась поверх
