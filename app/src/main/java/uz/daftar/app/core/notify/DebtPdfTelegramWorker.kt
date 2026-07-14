package uz.daftar.app.core.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import uz.daftar.app.core.backup.TelegramBackup
import uz.daftar.app.core.pdf.DebtPdf
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.domain.usecase.GetOverdueDebtorsUseCase
import java.time.LocalDate
import java.time.ZoneId

/**
 * v187: HAR KUNI 10:00 — barcha qarzdorlar (ism, summa, necha kun) BITTA ixcham PDF
 * bo'lib Telegramga yuboriladi. Internet bo'lmasa — WorkManager o'zi keyinroq yuboradi
 * (NetworkType.CONNECTED sharti). Token/chat sozlanmagan bo'lsa — jimgina o'tkazadi.
 */
@HiltWorker
class DebtPdfTelegramWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val getOverdue: GetOverdueDebtorsUseCase,
    private val telegram: TelegramBackup
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!telegram.isConfigured()) return Result.success()   // sozlanmagan — o'tkazamiz
        return try {
            val list = getOverdue(1L).sortedByDescending { it.debt }
            if (list.isEmpty()) return Result.success()

            val z = ZoneId.of("Asia/Tashkent")
            val today = LocalDate.now(z)
            val jami = list.sumOf { it.debt }

            // Ixcham: har mijoz bitta qator — joy tejaydi
            val body = list.mapIndexed { i, d ->
                "${i + 1}. ${d.client.replaceFirstChar { c -> c.uppercase() }} \u2014 ${d.debt.formatMoney()} so'm (${d.daysOverdue} kun)"
            }
            val file = DebtPdf.create(
                context = applicationContext,
                title = "Qarzdorlar \u2014 $today",
                headerLines = listOf("Jami: ${list.size} mijoz"),
                bodyLines = body,
                footerLines = listOf("JAMI QARZ: ${jami.formatMoney()} so'm")
            )
            val sent = telegram.sendFile(file, "qarzdorlar_$today.pdf")
            if (sent.isSuccess) Result.success()
            else if (runAttemptCount < 5) Result.retry() else Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.success()
        }
    }
}
