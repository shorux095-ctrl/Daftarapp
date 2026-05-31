package uz.daftar.app.core.backup

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import uz.daftar.app.data.db.DaftarDatabase
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * To'liq zaxira menejeri — butun SQLite bazasini (.db fayl) nusxalaydi.
 * Bu barcha jadvallarni (yozuvlar, narxlar, aliaslar, rasxod va h.k.) bir martada saqlaydi.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DaftarDatabase
) {
    private val stampFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    /** Ichki zaxiralar papkasi: filesDir/backups */
    fun backupDir(): File = File(context.filesDir, "backups").apply { mkdirs() }

    private fun dbFile(): File = context.getDatabasePath(DaftarDatabase.NAME)

    /** WAL jurnalini asosiy faylga birlashtirish (nusxadan oldin shart) */
    private fun checkpoint() {
        try {
            db.openHelper.writableDatabase
                .query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
                .use { it.moveToFirst() }
        } catch (_: Exception) { /* e'tiborsiz */ }
    }

    /** Ichki zaxira yaratadi va faylni qaytaradi */
    fun createInternalBackup(): File {
        checkpoint()
        val stamp = LocalDateTime.now().format(stampFmt)
        val target = File(backupDir(), "daftar_$stamp.db")
        dbFile().copyTo(target, overwrite = true)
        return target
    }

    /** Ichki zaxiralar ro'yxati (yangi → eski) */
    fun listBackups(): List<File> =
        backupDir().listFiles { f -> f.isFile && f.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun deleteBackup(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    /** Zaxirani tashqi joyga (SAF uri — Drive/Downloads) yozadi */
    fun exportTo(uri: Uri) {
        checkpoint()
        context.contentResolver.openOutputStream(uri)?.use { out ->
            dbFile().inputStream().use { it.copyTo(out) }
        }
    }

    /** Mavjud ichki zaxirani tashqi uri'ga ko'chiradi */
    fun exportFileTo(src: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        }
    }

    /**
     * Tiklash: berilgan oqimni vaqtinchalik faylga yozadi, SQLite ekanini tekshiradi,
     * so'ng asosiy baza faylini almashtiradi. Tiklangach ilovani qayta ishga tushirish kerak.
     * @return muvaffaqiyatli bo'lsa true
     */
    fun restoreFrom(input: InputStream): Boolean {
        val tmp = File(context.cacheDir, "restore_tmp.db")
        try {
            tmp.outputStream().use { input.copyTo(it) }
            if (!isSqlite(tmp)) { tmp.delete(); return false }
            db.close()
            val target = dbFile()
            // WAL/SHM yordamchi fayllarini o'chirish
            File(target.path + "-wal").delete()
            File(target.path + "-shm").delete()
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            return true
        } catch (e: Exception) {
            tmp.delete()
            return false
        }
    }

    fun restoreFromFile(file: File): Boolean = file.inputStream().use { restoreFrom(it) }

    fun restoreFromUri(uri: Uri): Boolean {
        val ins = context.contentResolver.openInputStream(uri) ?: return false
        return ins.use { restoreFrom(it) }
    }

    /** Faylning haqiqiy SQLite bazasi ekanini sarlavhasidan tekshiradi */
    private fun isSqlite(file: File): Boolean {
        if (file.length() < 16) return false
        val header = ByteArray(16)
        file.inputStream().use { it.read(header) }
        val sig = "SQLite format 3\u0000"
        return String(header, Charsets.ISO_8859_1) == sig
    }
}
