package uz.daftar.app.core.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uz.daftar.app.core.backup.BackupManager
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google hisob bilan kirib, bazani Drive'ga zaxiralash.
 * - drive.file ruxsati: ilova FAQAT o'zi yaratgan fayllarni ko'radi (maxfiy, xavfsiz).
 * - Har kunга bitta fayl: "daftar-YYYY-MM-DD.db". Eski 40 kundan oshgani avtomatik o'chadi.
 * - google-api kutubxonasisiz: access token + Drive REST (multipart) bilan.
 */
@Singleton
class DriveBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager
) {
    companion object {
        private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val FOLDER_NAME = "Daftar Backup"
        const val KEEP_DAYS = 40
    }

    fun signInClient(): GoogleSignInClient {
        val opts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE))
            .build()
        return GoogleSignIn.getClient(context, opts)
    }

    fun signInIntent(): Intent = signInClient().signInIntent

    fun signedInEmail(): String? =
        GoogleSignIn.getLastSignedInAccount(context)?.email

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { signInClient().signOut() }
        Unit
    }

    private fun token(): String {
        val acc = GoogleSignIn.getLastSignedInAccount(context)?.account
            ?: throw IllegalStateException("Google hisobiga kirilmagan")
        return GoogleAuthUtil.getToken(context, acc, "oauth2:$SCOPE")
    }

    private fun conn(urlStr: String, method: String, token: String): HttpURLConnection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30000; readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $token")
        }

    /** "Daftar Backup" papkasini topadi yoki yaratadi. */
    private fun ensureFolder(token: String): String {
        val q = URLEncoder.encode(
            "mimeType='application/vnd.google-apps.folder' and name='$FOLDER_NAME' and trashed=false",
            "UTF-8"
        )
        val c = conn("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name)", "GET", token)
        val resp = c.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(resp).optJSONArray("files")
        if (arr != null && arr.length() > 0) return arr.getJSONObject(0).getString("id")

        val create = conn("https://www.googleapis.com/drive/v3/files?fields=id", "POST", token)
        create.doOutput = true
        create.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        val meta = JSONObject()
            .put("name", FOLDER_NAME)
            .put("mimeType", "application/vnd.google-apps.folder")
        create.outputStream.use { it.write(meta.toString().toByteArray()) }
        val cr = create.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(cr).getString("id")
    }

    private fun findFile(token: String, folderId: String, name: String): String? {
        val q = URLEncoder.encode("name='$name' and '$folderId' in parents and trashed=false", "UTF-8")
        val c = conn("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)", "GET", token)
        val resp = c.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(resp).optJSONArray("files")
        return if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
    }

    /** Faylni Drive'ga yuklash (yangi yoki mavjudni yangilash) — multipart. */
    private fun uploadMultipart(token: String, fileId: String?, name: String, folderId: String, bytes: ByteArray) {
        val boundary = "daftarbnd" + System.currentTimeMillis()
        val meta = JSONObject().put("name", name)
        if (fileId == null) meta.put("parents", org.json.JSONArray().put(folderId))

        val url = if (fileId == null)
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        else
            "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=multipart"
        val method = if (fileId == null) "POST" else "PATCH"

        val c = conn(url, method, token)
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        c.outputStream.use { out ->
            val pre = ("--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                meta.toString() + "\r\n" +
                "--$boundary\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n").toByteArray()
            out.write(pre)
            out.write(bytes)
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = c.responseCode
        if (code !in 200..299) {
            val err = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("Drive yuklash xato $code: ${err.take(200)}")
        }
    }

    /** 40 kundan eski "daftar-*.db" fayllarni o'chirish. */
    private fun rotate(token: String, folderId: String) {
        val q = URLEncoder.encode("'$folderId' in parents and trashed=false and name contains 'daftar-'", "UTF-8")
        val c = conn(
            "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name)&orderBy=name desc&pageSize=200",
            "GET", token
        )
        val resp = c.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(resp).optJSONArray("files") ?: return
        val files = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.getString("name").matches(Regex("daftar-\\d{4}-\\d{2}-\\d{2}\\.db")) }
            .sortedByDescending { it.getString("name") }
        files.drop(KEEP_DAYS).forEach { f ->
            runCatching {
                conn("https://www.googleapis.com/drive/v3/files/${f.getString("id")}", "DELETE", token)
                    .responseCode
            }
        }
    }

    /** ASOSIY: bazani Drive'ga zaxiralash (bugungi fayl). */
    suspend fun backupNow(): String = withContext(Dispatchers.IO) {
        if (!isSignedIn()) throw IllegalStateException("Avval Google hisobiga kiring")
        backupManager.checkpointPublic()
        val src: File = backupManager.dbFilePublic()
        if (!src.exists()) throw IllegalStateException("Baza fayli topilmadi")
        val bytes = src.readBytes()
        val name = "daftar-${LocalDate.now()}.db"
        val tk = token()
        val folder = ensureFolder(tk)
        val existing = findFile(tk, folder, name)
        uploadMultipart(tk, existing, name, folder, bytes)
        rotate(tk, folder)
        "✅ Drive'ga saqlandi: $name"
    }

    /** Drive'dagi eng yangi "daftar-*.db" zaxira (id, nomi). */
    private fun latestBackup(token: String, folderId: String): Pair<String, String>? {
        val q = URLEncoder.encode("'$folderId' in parents and trashed=false and name contains 'daftar-'", "UTF-8")
        val c = conn(
            "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name)&orderBy=name desc&pageSize=200",
            "GET", token
        )
        val resp = c.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(resp).optJSONArray("files") ?: return null
        val files = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.getString("name").matches(Regex("daftar-\\d{4}-\\d{2}-\\d{2}\\.db")) }
            .sortedByDescending { it.getString("name") }
        val f = files.firstOrNull() ?: return null
        return f.getString("id") to f.getString("name")
    }

    /**
     * Drive'dagi eng yangi zaxirani yuklab, bazaga tiklaydi.
     * @return tiklangan fayl nomi yoki null (zaxira yo'q).
     */
    suspend fun restoreLatest(): String? = withContext(Dispatchers.IO) {
        if (!isSignedIn()) throw IllegalStateException("Avval Google hisobiga kiring")
        val tk = token()
        val folder = ensureFolder(tk)
        val latest = latestBackup(tk, folder) ?: return@withContext null
        val (id, name) = latest
        val c = conn("https://www.googleapis.com/drive/v3/files/$id?alt=media", "GET", tk)
        val bytes = c.inputStream.use { it.readBytes() }
        val ok = ByteArrayInputStream(bytes).use { backupManager.restoreFrom(it) }
        if (!ok) throw IllegalStateException("Tiklash xato: fayl yaroqsiz")
        name
    }
}
