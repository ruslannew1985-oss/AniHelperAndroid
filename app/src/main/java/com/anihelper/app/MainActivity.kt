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
import java.text.SimpleDateFormat
import java.util.Date
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
        val content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(40,60,40,40); setBackgroundColor(Color.rgb(15,15,18)) }
        content.addView(TextView(this).apply { text="浪人  РОНИН"; textSize=34f; setTextColor(Color.WHITE); gravity=Gravity.CENTER_HORIZONTAL })
        content.addView(TextView(this).apply { text="Твой самурай-агент"; textSize=18f; setTextColor(Color.rgb(220,40,40)); gravity=Gravity.CENTER_HORIZONTAL; setPadding(0,8,0,30) })
        answer=TextView(this).apply { text="Я Ронин. Назови задачу."; textSize=17f; setTextColor(Color.LTGRAY); setPadding(0,10,0,25) }; content.addView(answer)
        input=EditText(this).apply { hint="Например: таймер 10 минут"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); minLines=2 }; content.addView(input)
        content.addView(Button(this).apply { text="ВЫПОЛНИТЬ"; setOnClickListener { executeCommand(input.text.toString(),true) } })
        content.addView(Button(this).apply { text="🎤 ГОЛОСОВАЯ КОМАНДА"; setOnClickListener { startVoiceInput() } })
        content.addView(Button(this).apply { text="НАСТРОЙКИ ТЕЛЕФОНА"; setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) } })
        content.addView(TextView(this).apply { text="\nTELEGRAM"; textSize=18f; setTextColor(Color.WHITE) })
        tokenInput=EditText(this).apply { hint="Вставь новый токен BotFather"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setSingleLine(true); inputType=0x00000081; setText(prefs.getString("tg_token","")) }; content.addView(tokenInput)
        tgStatus=TextView(this).apply { text="Telegram: отключён"; setTextColor(Color.LTGRAY); setPadding(0,8,0,8) }; content.addView(tgStatus)
        content.addView(Button(this).apply { text="ПОДКЛЮЧИТЬ TELEGRAM"; setOnClickListener { val token=tokenInput.text.toString().trim(); if(token.isBlank()) tgStatus.text="Вставь токен BotFather" else { prefs.edit().putString("tg_token",token).apply(); startTelegram(token) } } })
        content.addView(Button(this).apply { text="ОТКЛЮЧИТЬ TELEGRAM"; setOnClickListener { telegramRunning=false; tgStatus.text="Telegram: отключён" } })
        content.addView(TextView(this).apply { text="\nРонин • локальный Android-агент"; textSize=14f; setTextColor(Color.DKGRAY); gravity=Gravity.CENTER_HORIZONTAL })
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun executeCommand(raw:String, local:Boolean):String {
        val command=raw.trim().lowercase(Locale.getDefault())
        if(command.isBlank()){ if(local) answer.text="Назови задачу."; return "Назови задачу." }
        val minutes=Regex("(\\d+)\\s*(мин|минут|минуты)").find(command)?.groupValues?.get(1)?.toIntOrNull()
        val now=Date()
        val reply=when {
            command=="/start" || command=="старт" -> "Ронин на связи ⚔️\nПиши команду обычным текстом. Напиши /help, чтобы увидеть возможности."
            command=="/help" || command.contains("что ты умеешь") || command=="помощь" -> "Команды Ронина:\n• привет\n• время\n• дата\n• таймер 10 минут\n• настройки (из приложения)\n• погода Мытищи\n\nЯ работаю без платного GPT API. Возможности будем расширять."
            command=="привет" || command.contains("привет ронин") -> "Привет. Ронин на связи ⚔️"
            command=="время" || command.contains("который час") || command.contains("сколько времени") -> "Сейчас ${SimpleDateFormat("HH:mm",Locale("ru","RU")).format(now)}."
            command=="дата" || command.contains("какое сегодня число") || command.contains("какая сегодня дата") -> "Сегодня ${SimpleDateFormat("d MMMM yyyy, EEEE",Locale("ru","RU")).format(now)}."
            command.contains("погод") -> getWeather(command)
            command.contains("таймер") && minutes!=null -> {
                if(local){ val i=Intent(AlarmClock.ACTION_SET_TIMER).apply { putExtra(AlarmClock.EXTRA_LENGTH,minutes*60); putExtra(AlarmClock.EXTRA_MESSAGE,"Ронин"); putExtra(AlarmClock.EXTRA_SKIP_UI,false) }; if(i.resolveActivity(packageManager)!=null) startActivity(i) }
                if(local) "Принято. Таймер на $minutes мин." else "Команду получил. Чтобы реально запустить таймер Android, открой Ронина на телефоне и скажи: таймер $minutes минут."
            }
            command.contains("настрой") -> { if(local){ startActivity(Intent(Settings.ACTION_SETTINGS)); "Открываю настройки." } else "Настройки телефона можно открыть только из приложения Ронин на самом телефоне." }
            command.contains("спасибо") -> "Всегда к услугам. ⚔️"
            command.contains("кто ты") -> "Я Ронин — твой локальный Android-помощник. Работаю на телефоне и принимаю команды через Telegram."
            else -> "Пока не знаю, как выполнить: «$raw». Напиши /help — покажу доступные команды."
        }
        if(local) answer.text=reply
        return reply
    }

    private fun getWeather(command:String):String {
        return try {
            val city=when {
                command.contains("моск") -> "Moscow"
                command.contains("мытищ") -> "Mytishchi"
                else -> "Mytishchi"
            }
            val json=JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude=${if(city=="Moscow") "55.7558" else "55.9116"}&longitude=${if(city=="Moscow") "37.6173" else "37.7308"}&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&timezone=Europe%2FMoscow"))
            val c=json.getJSONObject("current")
            val temp=c.optDouble("temperature_2m")
            val feels=c.optDouble("apparent_temperature")
            val rain=c.optDouble("precipitation")
            val wind=c.optDouble("wind_speed_10m")
            val code=c.optInt("weather_code")
            val condition=weatherText(code)
            val name=if(city=="Moscow") "Москве" else "Мытищах"
            "Сейчас в $name: $condition, ${temp}°C, ощущается как ${feels}°C. Осадки: ${rain} мм, ветер ${wind} км/ч."
        } catch(e:Exception){ "Не удалось получить погоду. Проверь интернет на телефоне." }
    }

    private fun weatherText(code:Int)=when(code){
        0->"ясно"; 1,2->"переменная облачность"; 3->"пасмурно"; 45,48->"туман"; 51,53,55,56,57->"морось"; 61,63,65,66,67->"дождь"; 71,73,75,77->"снег"; 80,81,82->"ливень"; 85,86->"снегопад"; 95,96,99->"гроза"; else->"облачно"
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
