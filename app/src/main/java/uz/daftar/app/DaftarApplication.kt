package uz.daftar.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import uz.daftar.app.core.notify.AutoReportWorker
import uz.daftar.app.core.notify.DebtReminderWorker
import uz.daftar.app.core.notify.DriveSyncWorker
import uz.daftar.app.core.notify.TelegramBackupWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import uz.daftar.app.core.notify.createReminderChannel
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class DaftarApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var backupManager: uz.daftar.app.core.backup.BackupManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        val appStart = System.currentTimeMillis()
        // Global xato tutuvchi (v149 — professional):
        // 1) Har qanday crash sababi faylga yoziladi (dasturchiga yuborish uchun)
        // 2) FON oqimidagi xato ilovani YIQITMAYDI (faqat logga yoziladi)
        // 3) Ochilishda ketma-ket 2 marta qulasa — keyingi safar XAVFSIZ REJIM (chat tarixi o'tkazib yuboriladi)
        runCatching {
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                runCatching {
                    val sw = java.io.StringWriter()
                    e.printStackTrace(java.io.PrintWriter(sw))
                    java.io.File(filesDir, "last_crash.txt").writeText(
                        "${java.util.Date()}\nOqim: ${t.name}\n${e}\n\n$sw"
                    )
                }
                if (t.name == "main") {
                    // Ochilish paytidagi (birinchi 15s) crash — hisoblanadi
                    runCatching {
                        val p = getSharedPreferences("crash_guard", MODE_PRIVATE)
                        if (System.currentTimeMillis() - appStart < 15_000) {
                            val n = p.getInt("startup_crashes", 0) + 1
                            p.edit().putInt("startup_crashes", n)
                                .putBoolean("safe_mode", n >= 2)
                                .apply()
                        }
                    }
                    prev?.uncaughtException(t, e)
                } else {
                    // FON oqimi xatosi — ilova ISHLAYVERADI, sabab logda
                    android.util.Log.e("DaftarGuard", "Fon oqimida tutildi (${t.name})", e)
                }
            }
        }
        // Ilova 15 soniya omon qolsa — ochilish muvaffaqiyatli, hisoblagich nolga qaytadi
        runCatching {
            android.os.Handler(mainLooper).postDelayed({
                getSharedPreferences("crash_guard", MODE_PRIVATE)
                    .edit().putInt("startup_crashes", 0).apply()
            }, 15_000)
        }
        // Juda katta eski log fayl qolgan bo'lsa — tozalash (xotira to'lib qulamasin)
        runCatching {
            val lf = java.io.File(filesDir, "app_log.txt")
            if (lf.exists() && lf.length() > 5_000_000L) lf.delete()
        }
        createReminderChannel(this)
        // Ilova o'z loglarini (xato/warning) faylga yozadi — "🐞 Xato loglari" menyusida ko'rinadi.
        startLogCapture()
        // Avtomatik bildirishnoma O'CHIRILDI — hisobotlar endi ilova ochilganda chatga chiqadi.
        scheduleDailyReminder()  // HAR KUNI 10:00 — bosh ekranga qarz eslatma KARTASI (bildirishnomasiz, yopiq bo'lsa ham)
        scheduleTelegramBackup() // har kuni 23:00 — bazani Telegramga (sozlangan bo'lsa)
        scheduleDriveSync()      // har 30 daqiqa — Drive'ga fon sinxron (internet bo'lganda; yo'q bo'lsa kutadi)
        scheduleNightlyLocalBackup() // v148: HAR KECHASI 02:00 — lokal avto-zaxira
        // v174: HAR KUNI 10:00 — qarz eslatma BILDIRISHNOMASI (ilova yopiq bo'lsa ham keladi)
        runCatching { uz.daftar.app.core.notify.scheduleDebtReminderAlarm(this) }
        scheduleDebtPdfTelegram()  // v187: har kuni 10:00 — qarzdorlar PDF Telegramga
        // Kunlik LOKAL zaxira — ilova ochilganda (har kuni kamida bitta .db nusxa, oxirgi 14 tasi saqlanadi)
        Thread { runCatching { backupManager.dailyLocalBackupIfNeeded() } }.apply { isDaemon = true }.start()
        // scheduleDailyAutoReport()
    }

    /** Ilovaning o'z logcat chiqishini (faqat shu ilova) faylga yozib boradi.
     *  OQ EKRAN yoki boshqa muammo bo'lsa — "🐞 Xato loglari" menyusida aniq sabab ko'rinadi. */
    private fun startLogCapture() {
        Thread {
            runCatching {
                val logFile = java.io.File(filesDir, "app_log.txt")
                // Eski jarayonning logini ham olish uchun avval dump (-d emas, davomiy o'qiymiz)
                val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "time"))
                val reader = proc.inputStream.bufferedReader()
                val buf = StringBuilder()
                var lastWrite = 0L
                reader.forEachLine { line ->
                    val important = line.contains("E/") || line.contains("Exception") ||
                            line.contains("AndroidRuntime") || line.contains("FATAL") ||
                            line.contains("Error")
                    val keep = important || line.contains("W/") ||
                            line.contains("daftar", true) || line.contains("Compose")
                    if (keep) {
                        buf.append(line).append('\n')
                        if (buf.length > 90000) buf.delete(0, buf.length - 60000)
                        val now = System.currentTimeMillis()
                        if (important || now - lastWrite > 2000) {
                            lastWrite = now
                            runCatching { logFile.writeText(buf.toString()) }
                        }
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * Har 30 daqiqa — bazani Google Drive'ga fon sinxron.
     * INTERNET SHART (constraint): internet yo'q bo'lsa WorkManager KUTADI,
     * internet qaytgach avtomatik yuboradi — ma'lumot yo'qolmaydi.
     */
    private fun scheduleDriveSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "drive_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Har kuni 08:00 — zaxira + kechagi/haftalik/oylik hisobot */
    private fun scheduleDailyAutoReport() {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
        }
        val delay = target.timeInMillis - now.timeInMillis
        val request = PeriodicWorkRequestBuilder<AutoReportWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_auto_report",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Har kuni 10:00 da qarz eslatma ishini rejalashtiradi */
    /** v187: HAR KUNI 10:00 — qarzdorlar PDF Telegramga (internet bo'lmasa keyinroq o'zi yuboradi) */
    private fun scheduleDebtPdfTelegram() {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
        }
        val delay = target.timeInMillis - now.timeInMillis
        val request = PeriodicWorkRequestBuilder<uz.daftar.app.core.notify.DebtPdfTelegramWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_debt_pdf_tg",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun scheduleDailyReminder() {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
        }
        val delay = target.timeInMillis - now.timeInMillis
        val request = PeriodicWorkRequestBuilder<DebtReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_debt_reminder",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Har kuni 23:00 da bazani Telegramga zaxiralash ishini rejalashtiradi */
    private fun scheduleTelegramBackup() {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
        }
        val delay = target.timeInMillis - now.timeInMillis
        val request = PeriodicWorkRequestBuilder<TelegramBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_telegram_backup",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** v148: HAR KECHASI 02:00 — bazaning lokal avto-zaxirasi (internetsiz ham ishlaydi). */
    private fun scheduleNightlyLocalBackup() {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
        }
        val delay = target.timeInMillis - now.timeInMillis
        val request = PeriodicWorkRequestBuilder<uz.daftar.app.core.notify.LocalBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "nightly_local_backup",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
