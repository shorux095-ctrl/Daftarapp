package uz.daftar.app.ui.screen.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.domain.usecase.GetDailyReportUseCase
import uz.daftar.app.domain.usecase.GetMonthlyReportUseCase
import uz.daftar.app.domain.usecase.GetYearlyReportUseCase
import uz.daftar.app.domain.usecase.PeriodReport
import java.time.LocalDate
import javax.inject.Inject

enum class ReportPeriod { DAY, MONTH, YEAR }

data class ReportsState(
    val period: ReportPeriod = ReportPeriod.DAY,
    val date: LocalDate = LocalDate.now(),
    val report: PeriodReport? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val getDaily: GetDailyReportUseCase,
    private val getMonthly: GetMonthlyReportUseCase,
    private val getYearly: GetYearlyReportUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    init { load() }

    fun setPeriod(p: ReportPeriod) {
        _state.update { it.copy(period = p) }
        load()
    }

    fun setDate(d: LocalDate) {
        _state.update { it.copy(date = d) }
        load()
    }

    fun load() {
        val s = state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val rep = when (s.period) {
                    ReportPeriod.DAY -> getDaily(userId, s.date)
                    ReportPeriod.MONTH -> getMonthly(userId, s.date.year, s.date.monthValue)
                    ReportPeriod.YEAR -> getYearly(userId, s.date.year)
                }
                _state.update { it.copy(report = rep, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Xato") }
            }
        }
    }
}
