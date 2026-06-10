package uz.daftar.app.ui.screen.grafik

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.domain.usecase.GetMonthlyTrendUseCase
import uz.daftar.app.domain.usecase.MonthPoint
import javax.inject.Inject

data class GrafikState(
    val isLoading: Boolean = true,
    val points: List<MonthPoint> = emptyList(),
    val months: Int = 6,
    val error: String? = null
)

@HiltViewModel
class GrafikViewModel @Inject constructor(
    private val getTrend: GetMonthlyTrendUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(GrafikState())
    val state: StateFlow<GrafikState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val pts = getTrend(userId, _state.value.months)
                _state.update { it.copy(isLoading = false, points = pts, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setMonths(m: Int) {
        if (m == _state.value.months) return
        _state.update { it.copy(months = m) }
        load()
    }
}
