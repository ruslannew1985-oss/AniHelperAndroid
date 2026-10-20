package com.anihelper.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var answer: TextView
    private val voiceRequest = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Ронин"

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
            setBackgroundColor(Color.rgb(15, 15, 18))
        }

        content.addView(TextView(this).apply {
            text = "浪人  РОНИН"
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        content.addView(TextView(this).apply {
            text = "Твой самурай-агент"
            textSize = 18f
            setTextColor(Color.rgb(220, 40, 40))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 8, 0, 30)
        })
        answer = TextView(this).apply {
            text = "Я Ронин. Назови задачу — выполню то, что доступно на телефоне."
            textSize = 17f
            setTextColor(Color.LTGRAY)
            setPadding(0, 10, 0, 25)
        }
        content.addView(answer)
        input = EditText(this).apply {
            hint = "Например: таймер 10 минут"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            minLines = 2
        }
        content.addView(input)
        content.addView(Button(this).apply {
            text = "ВЫПОЛНИТЬ"
            setOnClickListener { executeCommand(input.text.toString()) }
        })
        content.addView(Button(this).apply {
            text = "🎤 ГОЛОСОВАЯ КОМАНДА"
            setOnClickListener { startVoiceInput() }
        })
        content.addView(Button(this).apply {
            text = "НАСТРОЙКИ ТЕЛЕФОНА"
            setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        })
        content.addView(TextView(this).apply {
            text = "\nРонин • локальный Android-агент"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun executeCommand(raw: String) {
        val command = raw.trim().lowercase(Locale.getDefault())
        if (command.isBlank()) { answer.text = "Назови задачу."; return }
        val minutes = Regex("(\\d+)\\s*(мин|минут|минуты)").find(command)?.groupValues?.get(1)?.toIntOrNull()
        when {
            command.contains("таймер") && minutes != null -> {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Ронин")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    answer.text = "Принято. Таймер на $minutes мин."
                } else answer.text = "Приложение таймера не найдено."
            }
            command.contains("настрой") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                answer.text = "Открываю настройки."
            }
            command == "привет" || command.contains("привет ронин") -> answer.text = "Привет. Ронин на связи."
            command.contains("что ты умеешь") || command.contains("помощь") -> answer.text = "Сейчас я принимаю голосовые команды, ставлю таймеры и открываю системные настройки. Возможности будем расширять."
            else -> answer.text = "Команда принята: «$raw». Для этой задачи мне ещё нужен дополнительный инструмент."
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Команда для Ронина")
        }
        try { startActivityForResult(intent, voiceRequest) }
        catch (_: Exception) { answer.text = "Голосовой ввод недоступен." }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == voiceRequest && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            input.setText(text)
            executeCommand(text)
        }
    }
}
