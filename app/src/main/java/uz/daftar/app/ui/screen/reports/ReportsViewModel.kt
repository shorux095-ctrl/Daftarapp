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
import uz.daftar.app.domain.usecase.GetAllClientsUseCase
import uz.daftar.app.domain.usecase.ClientSummary
import uz.daftar.app.domain.usecase.PeriodReport
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class ReportPeriod { DAY, MONTH, YEAR, FOYDA }

data class ReportsState(
    val period: ReportPeriod = ReportPeriod.DAY,
    val date: LocalDate = LocalDate.now(),
    val report: PeriodReport? = null,
    val monthReport: PeriodReport? = null,
    val yearReport: PeriodReport? = null,
    val topDebtors: List<ClientSummary> = emptyList(),
    val inactiveClients: List<ClientSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val getDaily: GetDailyReportUseCase,
    private val getMonthly: GetMonthlyReportUseCase,
    private val getYearly: GetYearlyReportUseCase,
    private val getAllClients: GetAllClientsUseCase
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
                if (s.period == ReportPeriod.FOYDA) {
                    // Foyda: joriy oy (tepada) + joriy yil sof foyda
                    val now = LocalDate.now()
                    val monthRep = getMonthly(userId, now.year, now.monthValue)
                    val yearRep = getYearly(userId, now.year)
                    _state.update {
                        it.copy(monthReport = monthRep, yearReport = yearRep, isLoading = false)
                    }
                    return@launch
                }
                val rep = when (s.period) {
                    ReportPeriod.DAY -> getDaily(userId, s.date)
                    ReportPeriod.MONTH -> getMonthly(userId, s.date.year, s.date.monthValue)
                    ReportPeriod.YEAR -> getYearly(userId, s.date.year)
                    ReportPeriod.FOYDA -> getDaily(userId, s.date) // erishilmaydi
                }
                // Analitika: top qarzdorlar + faolsiz mijozlar
                val clients = getAllClients(userId)
                val top = clients.filter { it.debt > 0 }
                    .sortedByDescending { it.debt }
                    .take(5)
                val today = LocalDate.now()
                val inactive = clients.filter { c ->
                    val d = c.lastYukDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    d != null && ChronoUnit.DAYS.between(d, today) >= 30
                }.sortedBy { it.lastYukDate }.take(10)
                _state.update {
                    it.copy(
                        report = rep,
                        topDebtors = top,
                        inactiveClients = inactive,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Xato") }
            }
        }
    }
}
