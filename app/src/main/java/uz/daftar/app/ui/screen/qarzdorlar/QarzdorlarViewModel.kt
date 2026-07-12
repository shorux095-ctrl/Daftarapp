package uz.daftar.app.ui.screen.qarzdorlar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.domain.usecase.GetOverdueDebtorsUseCase
import uz.daftar.app.domain.usecase.OverdueDebtor
import javax.inject.Inject

data class QarzdorlarState(
    val debtors: List<OverdueDebtor> = emptyList(),
    val totalDebt: Long = 0,
    val loading: Boolean = true,
    val rating: Boolean = true,  // v148: 🏆 reyting (katta qarz yuqorida) / 📅 kun bo'yicha
    val eslatma: Boolean = false // v177: 🔔 guruhli ko'rinish (10/15/30/60/90 kun)
)

@HiltViewModel
class QarzdorlarViewModel @Inject constructor(
    private val getOverdue: GetOverdueDebtorsUseCase
) : ViewModel() {

    private val userId = 1L
    private val _state = MutableStateFlow(QarzdorlarState())
    val state: StateFlow<QarzdorlarState> = _state.asStateFlow()

    private var raw: List<OverdueDebtor> = emptyList()

    init { load() }

    fun load() {
        viewModelScope.launch {
            raw = runCatching { getOverdue(userId) }.getOrDefault(emptyList())
            applySort()
        }
    }

    /** v148: 🏆 Reyting — eng katta qarz yuqorida; 📅 Kun — yangi qarzlar yuqorida */
    fun setRating(r: Boolean) {
        _state.update { it.copy(rating = r) }

    /** v177: 🔔 guruhli eslatma ko'rinishini yoqish/o'chirish */
    fun toggleEslatma() {
        _state.update { it.copy(eslatma = !it.eslatma) }
    }
        applySort()
    }

    private fun applySort() {
        val s = _state.value
        val sorted = if (s.rating) raw.sortedByDescending { it.debt } else raw.sortedBy { it.daysOverdue }
        _state.update {
            it.copy(
                debtors = sorted,
                totalDebt = raw.sumOf { d -> d.debt },
                loading = false
            )
        }
    }
}
