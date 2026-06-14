package uz.daftar.app.core.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Belgilangan vaqtda AlarmManager chaqiradi — bildirishnoma ko'rsatamiz. */
class EslatmaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "Eslatma"
        val id = intent.getIntExtra("notif_id", text.hashCode())
        createReminderChannel(context)
        postEslatma(context, text, id)
    }
}

/** Telefon qayta yoqilganda — kelajakdagi eslatmalarni qayta rejalashtiradi. */
@AndroidEntryPoint
class EslatmaBootReceiver : BroadcastReceiver() {
    @Inject lateinit var dao: uz.daftar.app.data.db.dao.EslatmaDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val now = System.currentTimeMillis()
                dao.all(1L).first()
                    .filter { !it.done && it.triggerAt > now }
                    .forEach { scheduleEslatmaAlarm(context, it.id, it.text, it.triggerAt) }
            }
            pending.finish()
        }
    }
}

/** ANIQ vaqtga alarm qo'yish (Doze rejimida ham ishlaydi). */
fun scheduleEslatmaAlarm(context: Context, id: Long, text: String, triggerAt: Long) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, EslatmaReceiver::class.java)
        .putExtra("text", text)
        .putExtra("notif_id", (id % Int.MAX_VALUE).toInt())
    val pi = PendingIntent.getBroadcast(
        context, (id % Int.MAX_VALUE).toInt(), intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
}

fun cancelEslatmaAlarm(context: Context, id: Long) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, EslatmaReceiver::class.java)
    val pi = PendingIntent.getBroadcast(
        context, (id % Int.MAX_VALUE).toInt(), intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    am.cancel(pi)
}
