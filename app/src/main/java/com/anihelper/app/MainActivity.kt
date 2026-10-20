package com.anihelper.app

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognizerIntent
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
        title = "Юки Агент"

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }
        content.addView(TextView(this).apply { text = "Юки"; textSize = 34f })
        content.addView(TextView(this).apply {
            text = "Личный агент на телефоне"
            textSize = 18f
            setPadding(0, 8, 0, 28)
        })
        answer = TextView(this).apply {
            text = "Привет! Напиши или скажи команду. Я умею ставить таймер и открывать настройки телефона."
            textSize = 17f
            setPadding(0, 10, 0, 25)
        }
        content.addView(answer)
        input = EditText(this).apply { hint = "Например: таймер 10 минут"; minLines = 2 }
        content.addView(input)
        content.addView(Button(this).apply { text = "Выполнить"; setOnClickListener { executeCommand(input.text.toString()) } })
        content.addView(Button(this).apply { text = "🎤 Сказать голосом"; setOnClickListener { startVoiceInput() } })
        content.addView(Button(this).apply { text = "Настройки телефона"; setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) } })
        content.addView(TextView(this).apply {
            text = "\nЮки работает как локальный помощник. Следующий этап — подключение ИИ-мозга и дополнительных инструментов Android."
            textSize = 14f
        })
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun executeCommand(raw: String) {
        val command = raw.trim().lowercase(Locale.getDefault())
        if (command.isBlank()) { answer.text = "Напиши команду."; return }
        val minutes = Regex("(\\d+)\\s*(мин|минут|минуты)").find(command)?.groupValues?.get(1)?.toIntOrNull()
        when {
            command.contains("таймер") && minutes != null -> {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Юки")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
                if (intent.resolveActivity(packageManager) != null) { startActivity(intent); answer.text = "Ставлю таймер на $minutes мин." }
                else answer.text = "На телефоне не найдено приложение таймера."
            }
            command.contains("настрой") -> { startActivity(Intent(Settings.ACTION_SETTINGS)); answer.text = "Открываю настройки телефона." }
            command == "привет" || command.contains("привет юки") -> answer.text = "Привет! Я Юки. Готова помогать 😊"
            command.contains("что ты умеешь") || command.contains("помощь") -> answer.text = "Сейчас: голосовой ввод, таймеры и системные настройки. Дальше подключим ИИ-мозг и больше действий."
            else -> answer.text = "Я получила команду: «$raw». Свободный ИИ-диалог подключим следующим этапом."
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажи команду Юки")
        }
        try { startActivityForResult(intent, voiceRequest) }
        catch (_: Exception) { answer.text = "Голосовой ввод недоступен на этом телефоне." }
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
