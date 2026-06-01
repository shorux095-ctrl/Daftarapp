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
import uz.daftar.app.core.backup.BackupManager
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.domain.usecase.GetDailyReportUseCase
import uz.daftar.app.domain.usecase.GetMonthlyReportUseCase
import uz.daftar.app.domain.usecase.GetOverdueDebtorsUseCase
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Har kuni 08:00 da ishlaydi (botdagi avtomatik xabarlar kabi):
 *  - DB zaxira nusxa (oxirgi 14 ta saqlanadi)
 *  - Kechagi kunlik hisobot bildirishnomasi
 *  - Dushanba bo'lsa: haftalik xulosa
 *  - Oy 1-kuni bo'lsa: o'tgan oy hisoboti + umumiy qarz
 */
@HiltWorker
class AutoReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dailyReport: GetDailyReportUseCase,
    private val monthlyReport: GetMonthlyReportUseCase,
    private val getOverdue: GetOverdueDebtorsUseCase,
    private val backup: BackupManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = 1L
        // 1) Avtomatik zaxira + eski nusxalarni tozalash (oxirgi 14)
        runCatching {
            backup.createInternalBackup()
            val all = backup.listBackups().sortedByDescending { it.lastModified() }
            if (all.size > 14) all.drop(14).forEach { backup.deleteBackup(it) }
        }

        val today = LocalDate.now()

        // 2) Kechagi kunlik hisobot
        runCatching {
            val y = today.minusDays(1)
            val r = dailyReport(uid, y)
            val body = buildString {
                append("📅 ${r.rangeLabel}\n")
                append("💰 Daromad: ${r.revenue.formatMoney()}\n")
                append("📈 Foyda: ${r.grossProfit.formatMoney()}\n")
                append("💵 To'lov: ${r.payments.formatMoney()}\n")
                append("🧾 ${r.transactionCount} yozuv, ${r.clientCount} mijoz")
            }
            post(applicationContext, 1002, "📊 Kechagi hisobot", body)
        }

        // 3) Dushanba — haftalik xulosa (oxirgi 7 kun)
        if (today.dayOfWeek == DayOfWeek.MONDAY) {
            runCatching {
                var rev = 0L; var prof = 0L; var pay = 0L
                for (i in 1..7) {
                    val rr = dailyReport(uid, today.minusDays(i.toLong()))
                    rev += rr.revenue; prof += rr.grossProfit; pay += rr.payments
                }
                val body = "📆 Oxirgi 7 kun\n💰 Daromad: ${rev.formatMoney()}\n📈 Foyda: ${prof.formatMoney()}\n💵 To'lov: ${pay.formatMoney()}"
                post(applicationContext, 1003, "📆 Haftalik xulosa", body)
            }
        }

        // 4) Oy 1-kuni — o'tgan oy + umumiy qarz
        if (today.dayOfMonth == 1) {
            runCatching {
                val prev = today.minusMonths(1)
                val m = monthlyReport(uid, prev.year, prev.monthValue)
                val totalDebt = getOverdue(uid).sumOf { it.debt }
                val body = buildString {
                    append("📆 ${m.rangeLabel}\n")
                    append("💰 Daromad: ${m.revenue.formatMoney()}\n")
                    append("📈 Foyda: ${m.grossProfit.formatMoney()}\n")
                    append("🎯 Sof foyda: ${m.profit.formatMoney()}\n")
                    append("💳 Umumiy qarz: ${totalDebt.formatMoney()} so'm")
                }
                post(applicationContext, 1004, "📆 Oylik hisobot", body)
            }
        }

        return Result.success()
    }

    private fun post(context: Context, id: Int, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(body.substringBefore("\n"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notif)
        } catch (_: SecurityException) {
        }
    }
}
