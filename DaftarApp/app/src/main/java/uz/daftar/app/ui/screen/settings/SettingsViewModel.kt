package uz.daftar.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.daftar.app.core.security.LockManager
import uz.daftar.app.data.repository.TransactionRepository
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val lockManager: LockManager,
    private val repo: TransactionRepository
) : ViewModel() {

    private val userId: Long = 1L

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
