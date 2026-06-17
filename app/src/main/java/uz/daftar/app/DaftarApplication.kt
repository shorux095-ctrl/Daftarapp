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
import uz.daftar.app.core.notify.TelegramBackupWorker
import uz.daftar.app.core.notify.createReminderChannel
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class DaftarApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Global xato tutuvchi — har qanday crash sababini faylga yozadi,
        // keyingi ochilishda chatda ko'rsatamiz (dasturchiga yuborish uchun).
        runCatching {
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                runCatching {
                    val sw = java.io.StringWriter()
                    e.printStackTrace(java.io.PrintWriter(sw))
                    java.io.File(filesDir, "last_crash.txt").writeText(
                        "${java.util.Date()}\n${e}\n\n$sw"
                    )
                }
                prev?.uncaughtException(t, e)
            }
        }
        createReminderChannel(this)
        // Ilova o'z loglarini (xato/warning) faylga yozadi — "🐞 Xato loglari" menyusida ko'rinadi.
        startLogCapture()
        // Avtomatik bildirishnoma O'CHIRILDI — hisobotlar endi ilova ochilganda chatga chiqadi.
        // scheduleDailyReminder() — bildirishnoma O'CHIRILDI (eslatma faqat bosh ekranda chiqadi)
        scheduleTelegramBackup() // har kuni 23:00 — bazani Telegramga (sozlangan bo'lsa)
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
}
