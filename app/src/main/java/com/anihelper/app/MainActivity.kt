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
import java.util.*

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
        BirthdayData.schedule(this)
        val prefs = getSharedPreferences("ronin", MODE_PRIVATE)
        val content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(40,60,40,40); setBackgroundColor(Color.rgb(15,15,18)) }
        content.addView(TextView(this).apply { text="浪人  РОНИН"; textSize=34f; setTextColor(Color.WHITE); gravity=Gravity.CENTER_HORIZONTAL })
        content.addView(TextView(this).apply { text="Твой самурай-агент"; textSize=18f; setTextColor(Color.rgb(220,40,40)); gravity=Gravity.CENTER_HORIZONTAL; setPadding(0,8,0,30) })
        answer=TextView(this).apply { text="Я Ронин. Назови задачу."; textSize=17f; setTextColor(Color.LTGRAY); setPadding(0,10,0,25) }; content.addView(answer)
        input=EditText(this).apply { hint="Например: ближайшие дни рождения"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); minLines=2 }; content.addView(input)
        content.addView(Button(this).apply { text="ВЫПОЛНИТЬ"; setOnClickListener { executeCommand(input.text.toString(),true) } })
        content.addView(Button(this).apply { text="🎤 ГОЛОСОВАЯ КОМАНДА"; setOnClickListener { startVoiceInput() } })
        content.addView(Button(this).apply { text="НАСТРОЙКИ ТЕЛЕФОНА"; setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) } })
        content.addView(TextView(this).apply { text="\nTELEGRAM"; textSize=18f; setTextColor(Color.WHITE) })
        tokenInput=EditText(this).apply { hint="Вставь новый токен BotFather"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setSingleLine(true); inputType=0x81; setText(prefs.getString("tg_token","")) }; content.addView(tokenInput)
        tgStatus=TextView(this).apply { text="Telegram: отключён"; setTextColor(Color.LTGRAY;); setPadding(0,8,0,8) }; content.addView(tgStatus)
        content.addView(Button(this).apply { text="ПОДКЛЮЧИТЬ TELEGRAM"; setOnClickListener { val t=tokenInput.text.toString().trim(); if(t.isBlank()) tgStatus.text="Вставь токен BotFather" else { prefs.edit().putString("tg_token",t).apply(); BirthdayData.schedule(this@MainActivity); startTelegram(t) } } })
        content.addView(Button(this).apply { text="ОТКЛЮЧИТЬ TELEGRAM"; setOnClickListener { telegramRunning=false; tgStatus.text="Telegram: отключён" } })
        content.addView(TextView(this).apply { text="\nРонин • локальный Android-агент"; textSize=14f; setTextColor(Color.DKGRAY); gravity=Gravity.CENTER_HORIZONTAL })
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun executeCommand(raw:String,local:Boolean):String {
        val c=raw.trim().lowercase(Locale.getDefault())
        if(c.isBlank()){ if(local) answer.text="Назови задачу."; return "Назови задачу." }
        val minutes=Regex("(\\d+)\\s*(мин|минут|минуты)").find(c)?.groupValues?.get(1)?.toIntOrNull()
        val now=Date()
        val r=when {
            c=="/start"||c=="старт" -> "Ронин на связи ⚔️\nНапиши /help, чтобы увидеть возможности."
            c=="/help"||c.contains("что ты умеешь")||c=="помощь" -> "Команды:\n• время / дата\n• погода Мытищи\n• ближайшие дни рождения\n• дни рождения\n• кто родился в мае\n• когда день рождения у Насти\n• таймер 10 минут (в приложении)"
            c.contains("ближайш")&&c.contains("рожд") -> nearestBirthdays()
            c=="дни рождения"||c=="/birthdays" -> allBirthdays()
            c.startsWith("кто родился в ") -> birthdaysInMonth(c.removePrefix("кто родился в ").trim())
            c.contains("когда день рождения у ") -> findBirthday(c.substringAfter("когда день рождения у ").trim())
            c=="привет"||c.contains("привет ронин") -> "Привет. Ронин на связи ⚔️"
            c=="время"||c.contains("который час")||c.contains("сколько времени") -> "Сейчас ${SimpleDateFormat("HH:mm",Locale("ru","RU")).format(now)}."
            c=="дата"||c.contains("сегодня число")||c.contains("сегодня дата") -> "Сегодня ${SimpleDateFormat("d MMMM yyyy, EEEE",Locale("ru","RU")).format(now)}."
            c.contains("погод") -> getWeather(c)
            c.contains("таймер")&&minutes!=null -> {
                if(local){ val i=Intent(AlarmClock.ACTION_SET_TIMER).apply { putExtra(AlarmClock.EXTRA_LENGTH,minutes*60); putExtra(AlarmClock.EXTRA_MESSAGE,"Ронин"); putExtra(AlarmClock.EXTRA_SKIP_UI,false) }; if(i.resolveActivity(packageManager)!=null) startActivity(i) }
                if(local) "Принято. Таймер на $minutes мин." else "Таймер Android запускается из приложения Ронин на телефоне."
            }
            c.contains("настрой") -> { if(local){ startActivity(Intent(Settings.ACTION_SETTINGS)); "Открываю настройки." } else "Настройки телефона открываются из приложения Ронин." }
            c.contains("спасибо") -> "Всегда к услугам. ⚔️"
            c.contains("кто ты") -> "Я Ронин — твой Android-помощник и Telegram-бот."
            else -> "Пока не знаю, как выполнить: «$raw». Напиши /help."
        }
        if(local) answer.text=r
        return r
    }

    private val monthNames=listOf("января","февраля","марта","апреля","мая","июня","июля","августа","сентября","октября","ноября","декабря")
    private fun allBirthdays()=BirthdayData.all.sortedWith(compareBy<Birthday>{it.month}.thenBy{it.day}).joinToString("\n") { "${it.day} ${monthNames[it.month-1]} — ${it.name}" }
    private fun birthdaysInMonth(q:String):String { val stems=listOf("январ","феврал","март","апрел","ма","июн","июл","август","сентябр","октябр","ноябр","декабр"); val m=stems.indexOfFirst{q.startsWith(it)}+1; if(m==0)return "Не понял месяц."; val a=BirthdayData.all.filter{it.month==m}; return if(a.isEmpty()) "В этом месяце дней рождения нет." else a.joinToString("\n"){"${it.day} ${monthNames[m-1]} — ${it.name}"} }
    private fun findBirthday(q:String):String { val b=BirthdayData.all.find{it.name.lowercase().contains(q)||q.contains(it.name.lowercase())}; return if(b==null) "Не нашёл $q в списке." else "${b.name}: ${b.day} ${monthNames[b.month-1]}." }
    private fun nearestBirthdays():String {
        val now=Calendar.getInstance()
        val year=now.get(Calendar.YEAR)
        val list=BirthdayData.all.map { b ->
            val d=Calendar.getInstance().apply { set(year,b.month-1,b.day,0,0,0); set(Calendar.MILLISECOND,0) }
            if(d.before(now)) d.add(Calendar.YEAR,1)
            Pair(b,d)
        }.sortedBy { it.second.timeInMillis }.take(5)
        return "Ближайшие дни рождения:\n"+list.joinToString("\n") { "${it.first.day} ${monthNames[it.first.month-1]} — ${it.first.name}" }
    }
    private fun getWeather(c:String):String=try { val m=c.contains("моск"); val j=JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude=${if(m) "55.7558" else "55.9116"}&longitude=${if(m) "37.6173" else "37.7308"}&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&timezone=Europe%2FMoscow")); val x=j.getJSONObject("current"); "Сейчас в ${if(m) "Москве" else "Мытищах"}: ${x.optDouble("temperature_2m")}°C, ощущается ${x.optDouble("apparent_temperature")}°C, ветер ${x.optDouble("wind_speed_10m")} км/ч." } catch(_:Exception){ "Не удалось получить погоду. Проверь интернет." }
    private fun startTelegram(token:String){ if(telegramRunning)return; telegramRunning=true; tgStatus.text="Telegram: подключение…"; Thread { try { if(!JSONObject(httpGet("https://api.telegram.org/bot$token/getMe")).optBoolean("ok")) throw Exception("Неверный токен"); runOnUiThread{tgStatus.text="Telegram: подключён ✓"}; while(telegramRunning){ try { val arr=JSONObject(httpGet("https://api.telegram.org/bot$token/getUpdates?timeout=25&offset=$updateOffset")).optJSONArray("result")?:continue; for(i in 0 until arr.length()){ val u=arr.getJSONObject(i); updateOffset=u.optLong("update_id")+1; val msg=u.optJSONObject("message")?:continue; val text=msg.optString("text",""); val chat=msg.optJSONObject("chat")?.optLong("id")?:continue; getSharedPreferences("ronin",MODE_PRIVATE).edit().putLong("tg_chat_id",chat).apply(); if(text.isNotBlank())sendTelegram(token,chat,executeCommand(text,false)) } } catch(_:Exception){ runOnUiThread{tgStatus.text="Telegram: нет сети, переподключаюсь…"}; Thread.sleep(5000) } } } catch(e:Exception){ telegramRunning=false; runOnUiThread{tgStatus.text="Telegram: ошибка — ${e.message?:"нет связи"}"} } }.start() }
    private fun httpGet(a:String):String { val c=URL(a).openConnection() as HttpURLConnection; c.connectTimeout=15000; c.readTimeout=35000; return c.inputStream.bufferedReader().use{it.readText()} }
    private fun sendTelegram(t:String,id:Long,text:String){ httpGet("https://api.telegram.org/bot$t/sendMessage?chat_id=$id&text=${URLEncoder.encode(text,"UTF-8")}") }
    private fun startVoiceInput(){ val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU") }; try{startActivityForResult(i,voiceRequest)}catch(_:Exception){answer.text="Голосовой ввод недоступен."} }
    @Deprecated("Deprecated in Java") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){ super.onActivityResult(requestCode,resultCode,data); if(requestCode==voiceRequest&&resultCode==RESULT_OK){ val t=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty(); input.setText(t); executeCommand(t,true) } }
    override fun onDestroy(){ telegramRunning=false; super.onDestroy() }
}
