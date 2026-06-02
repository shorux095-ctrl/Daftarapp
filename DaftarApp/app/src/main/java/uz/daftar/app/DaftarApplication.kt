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
        createReminderChannel(this)
        // Avtomatik bildirishnoma O'CHIRILDI — hisobotlar endi ilova ochilganda chatga chiqadi.
        // Foydalanuvchi qo'ygan "Eslat" eslatmalari ishlashda davom etadi (alohida ishlaydi).
        // scheduleDailyReminder()
        // scheduleDailyAutoReport()
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

    /** Har kuni 11:00 da qarz eslatma ishini rejalashtiradi */
    private fun scheduleDailyReminder() {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 11)
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
}
