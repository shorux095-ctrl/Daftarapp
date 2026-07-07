package uz.daftar.app.core.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import uz.daftar.app.core.backup.BackupManager

/**
 * v148: AVTO ZAXIRA — har kechasi soat 02:00 da bazaning lokal nusxasini oladi.
 * Internet kerak emas (telefon ichida saqlanadi, oxirgi 14 kunlik nusxa turadi).
 * Google Drive sinxroniga TEGMAYDI — u alohida ishlayveradi.
 */
@HiltWorker
class LocalBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        // Kuniga 1 ta nusxa (02:00 da yangi kun boshlangan bo'ladi) + eski nusxalar tozalanadi
        backupManager.dailyLocalBackupIfNeeded(keep = 14)
        applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_BACKUP, System.currentTimeMillis())
            .apply()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val PREFS = "local_backup"
        const val KEY_LAST_BACKUP = "last_backup"
    }
}
