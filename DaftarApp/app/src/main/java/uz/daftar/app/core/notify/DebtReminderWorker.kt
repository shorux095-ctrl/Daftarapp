package uz.daftar.app.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import uz.daftar.app.MainActivity
import uz.daftar.app.domain.usecase.GetOverdueDebtorsUseCase

const val REMINDER_CHANNEL_ID = "debt_reminder"

/** Har kuni 11:00 da ishlab, muddati o'tgan qarzdorlar bo'lsa bildirishnoma yuboradi. */
@HiltWorker
class DebtReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val getOverdue: GetOverdueDebtorsUseCase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val list = try {
            getOverdue(1L)
        } catch (e: Exception) {
            return Result.success()
        }
        // 7+ kun muddati o'tganlar
        val overdue = list.filter { it.daysOverdue >= 7 }
        if (overdue.isNotEmpty()) {
            postReminder(applicationContext, overdue.size, overdue.maxOf { it.daysOverdue })
        }
        return Result.success()
    }
}

/** Bildirishnoma kanalini yaratadi (Application.onCreate'dan chaqiriladi) */
fun createReminderChannel(context: Context) {
    val channel = NotificationChannel(
        REMINDER_CHANNEL_ID,
        "Qarz eslatma",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Muddati o'tgan qarzlar haqida kunlik eslatma"
    }
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    mgr.createNotificationChannel(channel)
}

private fun postReminder(context: Context, count: Int, maxDays: Int) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pi = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notif = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("⏰ Qarz eslatma")
        .setContentText("$count mijoz qarzini $maxDays+ kun to'lamadi. \"eslatma\" deb ko'ring.")
        .setStyle(
            NotificationCompat.BigTextStyle().bigText(
                "$count ta mijoz qarzini muddatida to'lamadi (eng eskisi $maxDays kun). " +
                    "Ilovada \"eslatma\" yozib ko'ring."
            )
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pi)
        .setAutoCancel(true)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(1001, notif)
    } catch (_: SecurityException) {
        // POST_NOTIFICATIONS ruxsati yo'q — e'tiborsiz
    }
}
