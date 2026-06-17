package uz.daftar.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.daftar.app.core.security.LockManager
import uz.daftar.app.data.repository.TransactionRepository
import uz.daftar.app.domain.usecase.ImportOldDbUseCase
import java.time.LocalDateTime
import uz.daftar.app.core.drive.DriveBackup
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val lockManager: LockManager,
    private val repo: TransactionRepository,
    private val importOldDb: ImportOldDbUseCase,
    private val backup: uz.daftar.app.core.backup.BackupManager,
    private val drive: DriveBackup,
    private val telegram: uz.daftar.app.core.backup.TelegramBackup,
    private val themeManager: uz.daftar.app.core.theme.ThemeManager
) : ViewModel() {

    private val userId: Long = 1L

    private val _importMsg = MutableStateFlow<String?>(null)
    val importMsg: StateFlow<String?> = _importMsg.asStateFlow()
    fun clearImportMsg() { _importMsg.value = null }

    // ── Mavzu (ko'rinish): 0=Tizim, 1=Yorug', 2=Tungi ──
    val themeMode: StateFlow<Int> =
        themeManager.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    fun setThemeMode(m: Int) { viewModelScope.launch { themeManager.setThemeMode(m) } }

    // ── Avto-zaxira (fayl) ──
    private val _autoBackup = MutableStateFlow(backup.isAutoBackupEnabled())
    val autoBackup: StateFlow<Boolean> = _autoBackup.asStateFlow()
    fun enableAutoBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { backup.enableAutoBackup(uri) } }
            _autoBackup.value = true
        }
    }
    fun disableAutoBackup() {
        backup.setAutoBackupUri(null)
        _autoBackup.value = false
    }

    /** Eski bot .db ni import qiladi (Sozlamalardan) */
    fun importDb(path: String) {
        viewModelScope.launch {
            _importMsg.value = "⏳ Import boshlandi..."
            val r = withContext(Dispatchers.IO) { runCatching { importOldDb(userId, path) }.getOrNull() }
            _importMsg.value = if (r == null || !r.ok) "❌ Faylni o'qib bo'lmadi"
                else "📥 Import: ${r.tx} yozuv, ${r.price} narx, ${r.yukNarx} yuk narx, ${r.rasxod} rasxod"
        }
    }

    /** Eski CSV import (faqat yozuvlar) */
    fun importCsv(content: String) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                val entities = mutableListOf<uz.daftar.app.data.db.entity.TransactionEntity>()
                content.lines().forEachIndexed { idx, line ->
                    if (line.isBlank()) return@forEachIndexed
                    if (idx == 0 && line.lowercase().startsWith("client")) return@forEachIndexed
                    runCatching {
                        val p = line.split(",")
                        val client = p.getOrNull(0)?.trim().orEmpty()
                        val type = p.getOrNull(1)?.trim()?.lowercase().orEmpty()
                        val amount = p.getOrNull(2)?.trim()?.replace(",", ".")?.toDoubleOrNull()
                        val date = p.getOrNull(3)?.trim()?.replace("T", " ").orEmpty()
                        val tov = p.getOrNull(4)?.trim()?.takeIf { it.isNotBlank() }?.replace(",", ".")?.toDoubleOrNull()
                        if (client.isNotBlank() && type.isNotBlank() && amount != null && date.isNotBlank())
                            entities.add(uz.daftar.app.data.db.entity.TransactionEntity(userId = userId, clientName = client, type = type, amount = amount, date = date, tOverride = tov))
                    }
                }
                runCatching { repo.importTransactions(entities) }
                entities.size
            }
            _importMsg.value = "📥 CSV import: $count yozuv qo'shildi"
        }
    }

    /** Backup uchun barcha yozuvlarni CSV matn qilib qaytaradi */
    suspend fun buildBackupCsv(): String {
        val all = repo.getRange(
            userId,
            LocalDateTime.of(2000, 1, 1, 0, 0),
            LocalDateTime.of(2100, 1, 1, 0, 0)
        )
        val sb = StringBuilder()
        sb.append("client,type,amount,date,t_override\n")
        for (t in all) {
            val client = t.clientName.replace(",", " ")
            sb.append("$client,${t.type.code},${t.amount},${t.date},${t.tOverride ?: ""}\n")
        }
        return sb.toString()
    }

    val lockEnabled: StateFlow<Boolean> = lockManager.lockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** PIN o'rnatilganmi (kodning o'zini ko'rsatmaymiz) */
    val pinSet: StateFlow<Boolean> = lockManager.pinCode
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            lockManager.setLockEnabled(enabled)
        }
    }

    /** PIN o'rnatish (bo'sh bo'lsa — o'chiriladi) */
    fun setPin(pin: String?) {
        viewModelScope.launch {
            lockManager.setPinCode(pin)
        }
    }

    // ── Google Drive zaxira ──
    private val _driveEmail = MutableStateFlow(drive.signedInEmail())
    val driveEmail: StateFlow<String?> = _driveEmail.asStateFlow()

    private val _driveMsg = MutableStateFlow<String?>(null)
    val driveMsg: StateFlow<String?> = _driveMsg.asStateFlow()

    private val _driveBusy = MutableStateFlow(false)
    val driveBusy: StateFlow<Boolean> = _driveBusy.asStateFlow()

    fun signInClientIntent() = drive.signInIntent()

    fun onSignedIn() {
        _driveEmail.value = drive.signedInEmail()
        if (_driveBusy.value) return
        _driveBusy.value = true
        viewModelScope.launch {
            val count = runCatching { repo.countAll(userId) }.getOrDefault(0)
            if (count == 0) {
                // YANGI/BO'SH telefon — Drive'dan avtomatik tiklaymiz
                val res = runCatching { drive.restoreLatest() }
                _driveMsg.value = if (res.isSuccess) {
                    val name: String? = res.getOrNull()
                    if (name != null) "✅ Tiklandi: $name" else "✅ Kirildi (Drive'da zaxira yo'q)"
                } else {
                    "❌ Tiklash xato: " + (res.exceptionOrNull()?.message ?: "")
                }
            } else {
                // Ma'lumot bor — tiklamaymiz (xavfsizlik), darhol zaxiralaymiz
                val r = runCatching { drive.backupNow() }
                _driveMsg.value = r.getOrElse { "❌ " + (it.message ?: "xato") }
            }
            _driveBusy.value = false
        }
    }

    fun onSignInFailed(msg: String) { _driveMsg.value = "❌ Kirish xato: $msg" }

    fun signOutDrive() {
        viewModelScope.launch {
            drive.signOut()
            _driveEmail.value = null
            _driveMsg.value = "Google hisobidan chiqildi"
        }
    }

    fun backupNowDrive() {
        if (_driveBusy.value) return
        _driveBusy.value = true
        viewModelScope.launch {
            val r = runCatching { drive.backupNow() }
            _driveMsg.value = r.getOrElse { "❌ " + (it.message ?: "xato") }
            _driveBusy.value = false
        }
    }

    /** 2-telefon uchun: Drive'dan so'nggi zaxirani tortib, lokal bazani yangilaydi */
    fun refreshFromDrive() {
        if (_driveBusy.value) return
        _driveBusy.value = true
        viewModelScope.launch {
            val res = runCatching { drive.restoreLatest() }
            _driveMsg.value = if (res.isSuccess) {
                val name: String? = res.getOrNull()
                if (name != null) "✅ Yangilandi: $name — ilovani qayta oching" else "Drive'da zaxira topilmadi"
            } else "❌ Xato: " + (res.exceptionOrNull()?.message ?: "")
            _driveBusy.value = false
        }
    }

    fun clearDriveMsg() { _driveMsg.value = null }

    // ───────── Telegram zaxira ─────────
    private val _tgToken = MutableStateFlow(telegram.getToken())
    val tgToken: StateFlow<String> = _tgToken.asStateFlow()
    private val _tgChat = MutableStateFlow(telegram.getChatId())
    val tgChat: StateFlow<String> = _tgChat.asStateFlow()
    private val _tgConfigured = MutableStateFlow(telegram.isConfigured())
    val tgConfigured: StateFlow<Boolean> = _tgConfigured.asStateFlow()
    private val _tgMsg = MutableStateFlow<String?>(null)
    val tgMsg: StateFlow<String?> = _tgMsg.asStateFlow()
    private val _tgBusy = MutableStateFlow(false)
    val tgBusy: StateFlow<Boolean> = _tgBusy.asStateFlow()

    fun saveTelegram(token: String, chatId: String) {
        telegram.setConfig(token, chatId)
        _tgToken.value = telegram.getToken()
        _tgChat.value = telegram.getChatId()
        _tgConfigured.value = telegram.isConfigured()
        _tgMsg.value = if (_tgConfigured.value) "✅ Saqlandi" else "❌ Token va chat_id to'liq emas"
    }

    fun backupNowTelegram() {
        if (_tgBusy.value) return
        _tgBusy.value = true
        viewModelScope.launch {
            val r = telegram.sendBackup()
            _tgMsg.value = r.getOrElse { "❌ " + (it.message ?: "xato") }
            _tgConfigured.value = telegram.isConfigured()
            _tgBusy.value = false
        }
    }

    fun clearTgConfig() {
        telegram.clearConfig()
        _tgToken.value = ""; _tgChat.value = ""; _tgConfigured.value = false
        _tgMsg.value = "🗑 Telegram zaxira o'chirildi"
    }

    fun clearTgMsg() { _tgMsg.value = null }
}
