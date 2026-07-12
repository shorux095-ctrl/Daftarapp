package uz.daftar.app.core.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uz.daftar.app.MainActivity
import uz.daftar.app.domain.usecase.GetOverdueDebtorsUseCase
import java.util.Calendar
import javax.inject.Inject

/**
 * Har kuni soat 10:00 da ANIQ alarm bilan ishlaydi (Doze rejimida ham).
 * Qarzdorlar bo'lsa — bildirishnoma yuboradi va ertangi 10:00 ga qayta rejalashtiradi.
 */
@AndroidEntryPoint
class DebtReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var getOverdue: GetOverdueDebtorsUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            // v177: BILDIRISHNOMA YO'Q. Har kuni 10:00 da BOSH EKRAN chatiga eslatma kartasi yoziladi
            // (ilova yopiq bo'lsa ham — WorkManager va bu alarm ikkalasi ham ishlaydi, dublikat bo'lmaydi).
            runCatching {
                val list = getOverdue(1L).filter { it.daysOverdue >= 10 }
                if (list.isNotEmpty()) {
                    val store = uz.daftar.app.core.chat.ChatStore(context)
                    val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tashkent")).toString()
                    val json = store.load()
                    val arr = if (json.isBlank()) org.json.JSONArray()
                    else runCatching { org.json.JSONArray(json) }.getOrDefault(org.json.JSONArray())
                    // Bugun allaqachon qo'shilganmi? (worker qo'shган bo'lishi mumkin)
                    var already = false
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optString("k") == "debt") {
                            val d = java.time.Instant.ofEpochMilli(o.optLong("ts"))
                                .atZone(java.time.ZoneId.of("Asia/Tashkent")).toLocalDate().toString()
                            if (d == today) { already = true; break }
                        }
                    }
                    if (!already) {
                        arr.put(org.json.JSONObject().put("k", "debt").put("ts", System.currentTimeMillis()))
                        store.save(arr.toString())
                    }
                }
            }
            // Ertangi 10:00 ga qayta rejalashtirish (kunlik zanjir)
            runCatching { scheduleDebtReminderAlarm(context) }
            pending.finish()
        }
    }
}

private fun moneyDot(v: Long): String = "%,d".format(v).replace(',', '.')

private fun postDebtReminder(context: Context, count: Int, totalDebt: Long, maxDays: Int) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pi = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val totalStr = moneyDot(totalDebt)
    val notif = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("⏰ Qarz eslatma")
        .setContentText("$count mijoz qarzdor — jami $totalStr so'm")
        .setStyle(
            NotificationCompat.BigTextStyle().bigText(
                "$count ta mijozda jami $totalStr so'm qarz bor (eng eskisi $maxDays kun). " +
                    "Ilovani ochib ko'ring."
            )
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pi)
        .setAutoCancel(true)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(1001, notif)
    } catch (_: SecurityException) {
    }
}

/** Bildirishnoma O'CHIRILGAN — mavjud 10:00 alarmini bekor qiladi.
 *  Qarz eslatmasi faqat ilova bosh ekranida (chat kartasi) ko'rsatiladi. */
fun scheduleDebtReminderAlarm(context: Context) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, DebtReminderReceiver::class.java)
    val pi = PendingIntent.getBroadcast(
        context, 99001, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    // v174: keyingi soat 10:00 ni hisoblaymiz (agar bugun 10:00 o'tган bo'lsa — ertaga)
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 10)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
    }
    // ANIQ alarm (Doze rejimida ham ishlaydi). Ruxsat bo'lmasa — oddiy alarm.
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
        }
    } catch (_: SecurityException) {
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi) }
    }
}
