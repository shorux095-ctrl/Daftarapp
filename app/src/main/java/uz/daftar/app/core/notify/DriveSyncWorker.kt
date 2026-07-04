package uz.daftar.app.core.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import uz.daftar.app.core.drive.DriveBackup

/**
 * Fon rejimida bazani Google Drive'ga sinxronlaydi.
 * Har ~30 daqiqa ishlaydi (internet bo'lganda). Internet yo'q bo'lsa —
 * WorkManager avtomatik KUTADI va internet paydo bo'lgach ishga tushiradi (ma'lumot yo'qolmaydi).
 */
@HiltWorker
class DriveSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val drive: DriveBackup
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Drive ulanmagan bo'lsa — hech narsa qilmaymiz (xato emas)
        if (!drive.isSignedIn()) return Result.success()
        // 📱 2-TELEFON (ko'ruvchi) rejimi: bu telefon Drive'ga HECH QACHON yozmaydi —
        // asosiy telefon nusxasi buzilmasin (faqat "Drive'dan yangilash" bilan o'qiydi)
        val viewer = applicationContext.getSharedPreferences("device_mode", Context.MODE_PRIVATE)
            .getBoolean("viewer", false)
        if (viewer) return Result.success()
        return try {
            drive.backupNow()
            // Muvaffaqiyatli — oxirgi sync vaqtini saqlaymiz (status ko'rsatish uchun)
            applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
            Result.success()
        } catch (e: Exception) {
            // Internet yo'q / vaqtinchalik xato → keyinroq qayta uriniladi
            Result.retry()
        }
    }

    companion object {
        const val PREFS = "drive_sync"
        const val KEY_LAST_SYNC = "last_sync"
    }
}
