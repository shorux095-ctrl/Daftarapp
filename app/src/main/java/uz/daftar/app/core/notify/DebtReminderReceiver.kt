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
            runCatching {
                createReminderChannel(context)
                // Barcha qarzdorlar (muddatidan qat'i nazar — mayda/yangi qarzlar ham)
                val list = getOverdue(1L)
                if (list.isNotEmpty()) {
                    val total = list.sumOf { it.debt }
                    val maxDays = list.maxOf { it.daysOverdue }
                    postDebtReminder(context, list.size, total, maxDays)
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
    runCatching { am.cancel(pi) }
}
