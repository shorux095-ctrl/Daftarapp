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
    val loading: Boolean = true
)

@HiltViewModel
class QarzdorlarViewModel @Inject constructor(
    private val getOverdue: GetOverdueDebtorsUseCase
) : ViewModel() {

    private val userId = 1L
    private val _state = MutableStateFlow(QarzdorlarState())
    val state: StateFlow<QarzdorlarState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val list = runCatching { getOverdue(userId) }.getOrDefault(emptyList())
            // Kam kun yuqorida, ko'p kun pastda (10-14 kun tepada, 60 kun pastda)
            val sorted = list.sortedBy { it.daysOverdue }
            _state.update {
                QarzdorlarState(
                    debtors = sorted,
                    totalDebt = sorted.sumOf { d -> d.debt },
                    loading = false
                )
            }
        }
    }
}
