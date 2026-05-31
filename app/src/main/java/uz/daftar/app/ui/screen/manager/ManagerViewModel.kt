package uz.daftar.app.ui.screen.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.daftar.app.core.backup.BackupManager
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BackupItem(
    val file: File,
    val name: String,
    val dateLabel: String,
    val sizeLabel: String
)

data class ManagerState(
    val backups: List<BackupItem> = emptyList(),
    val message: String? = null,
    val restartNeeded: Boolean = false
)

@HiltViewModel
class ManagerViewModel @Inject constructor(
    private val backup: BackupManager
) : ViewModel() {

    private val _state = MutableStateFlow(ManagerState())
    val state: StateFlow<ManagerState> = _state.asStateFlow()

    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm")

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                backup.listBackups().map { f ->
                    val dt = LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(f.lastModified()),
                        ZoneId.systemDefault()
                    )
                    BackupItem(
                        file = f,
                        name = f.name,
                        dateLabel = dt.format(dateFmt),
                        sizeLabel = humanSize(f.length())
                    )
                }
            }
            _state.update { it.copy(backups = items) }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { backup.createInternalBackup() }.isSuccess
            }
            _state.update { it.copy(message = if (ok) "✅ Zaxira yaratildi" else "❌ Xatolik") }
            refresh()
        }
    }

    fun deleteBackup(item: BackupItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { backup.deleteBackup(item.file) }
            _state.update { it.copy(message = "🗑 O'chirildi") }
            refresh()
        }
    }

    fun restore(item: BackupItem) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { backup.restoreFromFile(item.file) }
            _state.update {
                it.copy(
                    message = if (ok) "✅ Tiklandi — ilova qayta ishga tushadi" else "❌ Tiklash xatosi",
                    restartNeeded = ok
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    // Tashqi (SAF) operatsiyalar uchun BackupManager'ни ochib beradi
    val manager: BackupManager get() = backup
}
