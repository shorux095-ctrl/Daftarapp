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
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val lockManager: LockManager,
    private val repo: TransactionRepository,
    private val importOldDb: ImportOldDbUseCase
) : ViewModel() {

    private val userId: Long = 1L

    private val _importMsg = MutableStateFlow<String?>(null)
    val importMsg: StateFlow<String?> = _importMsg.asStateFlow()
    fun clearImportMsg() { _importMsg.value = null }

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
}
