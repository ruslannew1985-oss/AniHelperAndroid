package com.anihelper.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

class BirthdayReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent?){
        val pending=goAsync()
        Thread {
            try {
                val prefs=context.getSharedPreferences("ronin",Context.MODE_PRIVATE)
                val token=prefs.getString("tg_token","").orEmpty()
                val chatId=prefs.getLong("tg_chat_id",0L)
                if(token.isBlank() || chatId==0L) return@Thread
                val now=Calendar.getInstance()
                val today=BirthdayData.all.filter { it.day==now.get(Calendar.DAY_OF_MONTH) && it.month==now.get(Calendar.MONTH)+1 }
                val future=(now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR,3) }
                val soon=BirthdayData.all.filter { it.day==future.get(Calendar.DAY_OF_MONTH) && it.month==future.get(Calendar.MONTH)+1 }
                val messages=mutableListOf<String>()
                if(today.isNotEmpty()) messages += "🎂 Сегодня день рождения: ${today.joinToString(", "){it.name}}. Не забудь поздравить!"
                if(soon.isNotEmpty()) messages += "🎁 Через 3 дня день рождения: ${soon.joinToString(", "){it.name}}."
                messages.forEach { send(token,chatId,it) }
            } catch(_:Exception){} finally { pending.finish() }
        }.start()
    }
    private fun send(token:String,chatId:Long,text:String){
        val encoded=URLEncoder.encode(text,"UTF-8")
        val c=URL("https://api.telegram.org/bot$token/sendMessage?chat_id=$chatId&text=$encoded").openConnection() as HttpURLConnection
        c.connectTimeout=15000;c.readTimeout=20000;c.inputStream.close()
    }
}

class BootReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent?){ BirthdayData.schedule(context) }
}
