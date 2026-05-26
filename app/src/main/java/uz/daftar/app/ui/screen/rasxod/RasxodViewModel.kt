package uz.daftar.app.ui.screen.rasxod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.entity.RasxodEntity
import uz.daftar.app.domain.usecase.AddRasxodUseCase
import uz.daftar.app.domain.usecase.DeleteRasxodUseCase
import uz.daftar.app.domain.usecase.GetRasxodRangeUseCase
import uz.daftar.app.domain.usecase.GetRasxodTotalUseCase
import java.time.LocalDate
import javax.inject.Inject

data class RasxodState(
    val items: List<RasxodEntity> = emptyList(),
    val monthlyTotal: Long = 0,
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class RasxodViewModel @Inject constructor(
    private val addUC: AddRasxodUseCase,
    private val deleteUC: DeleteRasxodUseCase,
    private val rangeUC: GetRasxodRangeUseCase,
    private val totalUC: GetRasxodTotalUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(RasxodState())
    val state: StateFlow<RasxodState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val today = LocalDate.now()
            val monthStart = today.withDayOfMonth(1)
            val items = rangeUC(userId, monthStart, today).sortedByDescending { it.date }
            val total = totalUC(userId, monthStart, today)
            _state.update { it.copy(items = items, monthlyTotal = total, isLoading = false) }
        }
    }

    fun add(amountStr: String, note: String) {
        val amount = amountStr.trim().replace(",", ".").toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _state.update { it.copy(message = "❌ Noto'g'ri summa") }
            return
        }
        viewModelScope.launch {
            addUC(userId, amount, note.trim())
            _state.update { it.copy(message = "✅ Rasxod qo'shildi") }
            load()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            deleteUC(id)
            _state.update { it.copy(message = "🗑 O'chirildi") }
            load()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
