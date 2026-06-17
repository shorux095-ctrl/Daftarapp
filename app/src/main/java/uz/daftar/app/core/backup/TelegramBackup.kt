package uz.daftar.app.core.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bazani (.db) Telegram bot orqali zaxiralash.
 * Foydalanuvchi bir marta bot token + chat_id kiritadi; keyin baza Telegramga yuboriladi
 * (qo'lda tugma yoki kunlik avtomatik). Shunday qilib ma'lumot telefondan TASHQARIDA saqlanadi —
 * telefon yo'qolsa/buzilsa ham qarz daftari Telegramda turadi (= barqarorlik).
 */
@Singleton
class TelegramBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupManager
) {
    private val prefs by lazy { context.getSharedPreferences("tg_backup", Context.MODE_PRIVATE) }
    private val stampFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    fun getToken(): String = prefs.getString("token", "") ?: ""
    fun getChatId(): String = prefs.getString("chat", "") ?: ""
    fun isConfigured(): Boolean = getToken().isNotBlank() && getChatId().isNotBlank()

    fun setConfig(token: String, chatId: String) {
        prefs.edit().putString("token", token.trim()).putString("chat", chatId.trim()).apply()
    }
    fun clearConfig() { prefs.edit().clear().apply() }

    /** Bazani Telegramga yuboradi. UI thread'da chaqirmang — IO'da ishlaydi. */
    suspend fun sendBackup(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = getToken()
            val chat = getChatId()
            if (token.isBlank() || chat.isBlank()) error("Token yoki chat_id kiritilmagan")
            backup.checkpointPublic()
            val src = backup.dbFilePublic()
            if (!src.exists() || src.length() < 16L) error("Baza fayli topilmadi")
            val stamp = LocalDateTime.now().format(stampFmt)
            val name = "daftar_$stamp.db"
            uploadDocument(token, chat, src, name)
            "✅ Telegramga yuborildi: $name"
        }
    }

    /** Telegram Bot API: sendDocument (multipart/form-data) */
    private fun uploadDocument(token: String, chatId: String, file: File, filename: String) {
        val boundary = "DaftarBoundary" + System.currentTimeMillis()
        val conn = (URL("https://api.telegram.org/bot$token/sendDocument")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 120000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                // chat_id maydoni
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
                out.writeBytes("$chatId\r\n")
                // caption (faqat ASCII)
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"caption\"\r\n\r\n")
                out.writeBytes("Daftar zaxira $filename\r\n")
                // document (fayl)
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"$filename\"\r\n")
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                file.inputStream().use { it.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull() ?: ""
                error("Telegram javobi: $code ${err.take(200)}")
            }
            runCatching { conn.inputStream.close() }
        } finally {
            conn.disconnect()
        }
    }
}
