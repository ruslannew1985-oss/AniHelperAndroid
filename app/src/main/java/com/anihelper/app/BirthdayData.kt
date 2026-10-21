package com.anihelper.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

data class Birthday(val name:String,val day:Int,val month:Int)

object BirthdayData {
    val all=listOf(
        Birthday("Вячеслав Калмыков",4,1),Birthday("Ариф",8,1),Birthday("Ирина Синельникова",14,1),Birthday("Назира",29,1),
        Birthday("Слава",13,2),Birthday("Аня",20,2),Birthday("Маша",24,2),Birthday("Назим",28,2),Birthday("Эта группа",1,3),
        Birthday("Света",4,3),Birthday("Лилия",25,3),Birthday("Сервер",2,4),Birthday("Лариса Валерьевна",7,4),Birthday("Настя",12,4),
        Birthday("Стас",14,4),Birthday("Эрика",28,4),Birthday("Полина",7,5),Birthday("Галина",8,5),Birthday("Марлен",8,5),Birthday("Тамара",9,5),
        Birthday("Елена Ким",17,5),Birthday("Евгений",12,6),Birthday("Татьяна",17,6),Birthday("Вероника",18,6),Birthday("Оля Бацак",20,6),
        Birthday("Оля Ли",3,7),Birthday("Зарина",6,7),Birthday("Ринат",22,7),Birthday("Надя",3,8),Birthday("Ваня",6,8),Birthday("Инна",9,8),
        Birthday("Фаниль",13,9),Birthday("Сергей",22,9),Birthday("Нияра",1,10),Birthday("Линара",2,10),Birthday("Азиз",8,10),Birthday("Руслан",23,10),
        Birthday("Олеся",26,10),Birthday("Александра Самгина",10,11),Birthday("Марат",18,11),Birthday("Юн Александра",19,11),Birthday("Вера Мамыкина",27,11),
        Birthday("Анатолий",29,11),Birthday("Костя",17,12),Birthday("Умид",19,12)
    )

    fun schedule(context:Context){
        val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent=Intent(context,BirthdayReceiver::class.java)
        val pi=PendingIntent.getBroadcast(context,9001,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cal=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,9); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0); if(timeInMillis<=System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR,1) }
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP,cal.timeInMillis,AlarmManager.INTERVAL_DAY,pi)
    }
}
