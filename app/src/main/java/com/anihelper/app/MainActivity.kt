package com.anihelper.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var answer: TextView
    private lateinit var tokenInput: EditText
    private lateinit var tgStatus: TextView
    private val voiceRequest = 1001
    @Volatile private var telegramRunning = false
    private var updateOffset = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Ронин"
        val prefs = getSharedPreferences("ronin", MODE_PRIVATE)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40,60,40,40); setBackgroundColor(Color.rgb(15,15,18)) }
        content.addView(TextView(this).apply { text="浪人  РОНИН"; textSize=34f; setTextColor(Color.WHITE); gravity=Gravity.CENTER_HORIZONTAL })
        content.addView(TextView(this).apply { text="Твой самурай-агент"; textSize=18f; setTextColor(Color.rgb(220,40,40)); gravity=Gravity.CENTER_HORIZONTAL; setPadding(0,8,0,30) })
        answer = TextView(this).apply { text="Я Ронин. Назови задачу."; textSize=17f; setTextColor(Color.LTGRAY); setPadding(0,10,0,25) }; content.addView(answer)
        input = EditText(this).apply { hint="Например: таймер 10 минут"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); minLines=2 }; content.addView(input)
        content.addView(Button(this).apply { text="ВЫПОЛНИТЬ"; setOnClickListener { executeCommand(input.text.toString(), true) } })
        content.addView(Button(this).apply { text="🎤 ГОЛОСОВАЯ КОМАНДА"; setOnClickListener { startVoiceInput() } })
        content.addView(Button(this).apply { text="НАСТРОЙКИ ТЕЛЕФОНА"; setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) } })

        content.addView(TextView(this).apply { text="\nTELEGRAM"; textSize=18f; setTextColor(Color.WHITE) })
        tokenInput = EditText(this).apply { hint="Вставь новый токен BotFather"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setSingleLine(true); inputType=0x00000081; setText(prefs.getString("tg_token", "")) }; content.addView(tokenInput)
        tgStatus = TextView(this).apply { text="Telegram: отключён"; setTextColor(Color.LTGRAY); setPadding(0,8,0,8) }; content.addView(tgStatus)
        content.addView(Button(this).apply { text="ПОДКЛЮЧИТЬ TELEGRAM"; setOnClickListener {
            val token=tokenInput.text.toString().trim()
            if(token.isBlank()){ tgStatus.text="Вставь токен BotFather" } else { prefs.edit().putString("tg_token",token).apply(); startTelegram(token) }
        }})
        content.addView(Button(this).apply { text="ОТКЛЮЧИТЬ TELEGRAM"; setOnClickListener { telegramRunning=false; tgStatus.text="Telegram: отключён" } })
        content.addView(TextView(this).apply { text="\nРонин • локальный Android-агент"; textSize=14f; setTextColor(Color.DKGRAY); gravity=Gravity.CENTER_HORIZONTAL })
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun executeCommand(raw:String, local:Boolean):String {
        val command=raw.trim().lowercase(Locale.getDefault())
        if(command.isBlank()){ if(local) answer.text="Назови задачу."; return "Назови задачу." }
        val minutes=Regex("(\\d+)\\s*(мин|минут|минуты)").find(command)?.groupValues?.get(1)?.toIntOrNull()
        val reply=when {
            command.contains("таймер") && minutes!=null -> {
                if(local){ val i=Intent(AlarmClock.ACTION_SET_TIMER).apply { putExtra(AlarmClock.EXTRA_LENGTH,minutes*60); putExtra(AlarmClock.EXTRA_MESSAGE,"Ронин"); putExtra(AlarmClock.EXTRA_SKIP_UI,false) }; if(i.resolveActivity(packageManager)!=null) startActivity(i) }
                if(local) "Принято. Таймер на $minutes мин." else "Команду получил. Таймер можно запустить из приложения Ронин на телефоне."
            }
            command.contains("настрой") -> { if(local) startActivity(Intent(Settings.ACTION_SETTINGS)); "Открываю настройки." }
            command=="привет" || command.contains("привет ронин") -> "Привет. Ронин на связи."
            command.contains("что ты умеешь") || command.contains("помощь") -> "Я работаю без платного GPT API. Принимаю команды в Telegram и в приложении; на телефоне могу ставить таймер и открывать настройки."
            else -> "Команда принята: «$raw». Эту функцию ещё нужно добавить в Ронина."
        }
        if(local) answer.text=reply
        return reply
    }

    private fun startTelegram(token:String){
        if(telegramRunning) return
        telegramRunning=true; tgStatus.text="Telegram: подключение…"
        Thread {
            try {
                val me=httpGet("https://api.telegram.org/bot$token/getMe")
                if(!JSONObject(me).optBoolean("ok")) throw Exception("Неверный токен")
                runOnUiThread { tgStatus.text="Telegram: подключён ✓" }
                while(telegramRunning){
                    val data=JSONObject(httpGet("https://api.telegram.org/bot$token/getUpdates?timeout=25&offset=$updateOffset"))
                    val arr=data.optJSONArray("result") ?: continue
                    for(i in 0 until arr.length()){
                        val u=arr.getJSONObject(i); updateOffset=u.optLong("update_id")+1
                        val msg=u.optJSONObject("message") ?: continue
                        val text=msg.optString("text",""); val chatId=msg.optJSONObject("chat")?.optLong("id") ?: continue
                        if(text.isNotBlank()) sendTelegram(token,chatId,executeCommand(text,false))
                    }
                }
            } catch(e:Exception){ telegramRunning=false; runOnUiThread { tgStatus.text="Telegram: ошибка — ${e.message ?: "нет связи"}" } }
        }.start()
    }

    private fun httpGet(address:String):String { val c=URL(address).openConnection() as HttpURLConnection; c.connectTimeout=15000; c.readTimeout=35000; return c.inputStream.bufferedReader().use { it.readText() } }
    private fun sendTelegram(token:String,chatId:Long,text:String){ val encoded=URLEncoder.encode(text,"UTF-8"); httpGet("https://api.telegram.org/bot$token/sendMessage?chat_id=$chatId&text=$encoded") }

    private fun startVoiceInput(){ val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU"); putExtra(RecognizerIntent.EXTRA_PROMPT,"Команда для Ронина") }; try{ startActivityForResult(i,voiceRequest) }catch(_:Exception){ answer.text="Голосовой ввод недоступен." } }
    @Deprecated("Deprecated in Java") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){ super.onActivityResult(requestCode,resultCode,data); if(requestCode==voiceRequest && resultCode==RESULT_OK){ val text=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty(); input.setText(text); executeCommand(text,true) } }
    override fun onDestroy(){ telegramRunning=false; super.onDestroy() }
}
