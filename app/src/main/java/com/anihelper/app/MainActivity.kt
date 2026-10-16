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
            text = "Ани — твоя помощница"
            textSize = 26f
        }

        val description = TextView(this).apply {
            text = "Нажми кнопку ниже, чтобы запустить Ани поверх других приложений."
            textSize = 18f
            setPadding(0, 40, 0, 40)
        }

        val startButton = Button(this).apply {
            text = "Запустить Ани"

            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {

                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )

                    startActivity(intent)

                } else {

                    val serviceIntent = Intent(
                        this@MainActivity,
                        FloatingAniService::class.java
                    )

                    startService(serviceIntent)
                }
            }
        }

        layout.addView(title)
        layout.addView(description)
        layout.addView(startButton)

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()

        if (Settings.canDrawOverlays(this)) {
            val serviceIntent = Intent(
                this,
                FloatingAniService::class.java
            )

            startService(serviceIntent)
        }
    }
}
