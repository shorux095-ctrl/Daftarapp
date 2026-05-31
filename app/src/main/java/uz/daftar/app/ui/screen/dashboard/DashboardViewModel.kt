package uz.daftar.app.ui.screen.dashboard

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
import uz.daftar.app.domain.usecase.GetYukReportUseCase
import java.time.YearMonth
import javax.inject.Inject

enum class DashPeriod { MONTH, YEAR }

data class DashPoint(val label: String, val foyda: Long)

data class DashboardState(
    val isLoading: Boolean = true,
    val period: DashPeriod = DashPeriod.MONTH,
    val title: String = "",
    val points: List<DashPoint> = emptyList(),
    val totalFoyda: Long = 0,   // ΣN − ΣT
    val totalSavdo: Long = 0,   // ΣN (sotilgan, mijoz narxida)
    val totalTolov: Long = 0    // ΣP
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val yuk: GetYukReportUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init { load(DashPeriod.MONTH) }

    fun setPeriod(p: DashPeriod) {
        if (p == _state.value.period && !_state.value.isLoading) return
        load(p)
    }

    private fun load(p: DashPeriod) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, period = p) }
            val report = withContext(Dispatchers.Default) {
                if (p == DashPeriod.MONTH) yuk.monthly(userId, YearMonth.now())
                else yuk.yearly(userId, YearMonth.now().year)
            }
            val pts = report.rows.map { DashPoint(it.label, it.nTotal - it.tTotal) }
            _state.update {
                it.copy(
                    isLoading = false,
                    title = report.title,
                    points = pts,
                    totalFoyda = report.jamiN - report.jamiT,
                    totalSavdo = report.jamiN,
                    totalTolov = report.jamiP
                )
            }
        }
    }
}
