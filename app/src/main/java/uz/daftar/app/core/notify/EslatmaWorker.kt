package uz.daftar.app.core.notify

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

/** Belgilangan vaqtda ishlab, eslatma bildirishnomasini ko'rsatadi. */
@HiltWorker
class EslatmaWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val text = inputData.getString("text") ?: "Eslatma"
        val notifId = inputData.getInt("notif_id", text.hashCode())
        postEslatma(applicationContext, text, notifId)
        return Result.success()
    }
}

fun postEslatma(context: Context, text: String, notifId: Int) {
    createReminderChannel(context)
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pi = PendingIntent.getActivity(
        context, notifId, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notif = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("⏰ Eslatma")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(notifId, notif)
    } catch (e: SecurityException) {
        // POST_NOTIFICATIONS ruxsati yo'q — e'tiborsiz
    }
}
