package uz.daftar.app.core.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import uz.daftar.app.core.backup.TelegramBackup

/** Har kuni bazani Telegramga zaxiralaydi (sozlangan bo'lsa). */
@HiltWorker
class TelegramBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val telegram: TelegramBackup
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!telegram.isConfigured()) return Result.success()
        return try {
            telegram.sendBackup()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
