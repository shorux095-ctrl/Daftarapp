package uz.daftar.app.ui.screen.toliq

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

data class ToliqState(
    val isLoading: Boolean = true,
    val mode: Int = 0,                 // 0 = Bugun, 1 = Shu oy, 2 = Yil, 3 = Sof foyda (12 oy)
    val selectedDate: LocalDate = LocalDate.now(),
    val report: PeriodReport? = null,
    val monthlyProfits: List<uz.daftar.app.domain.usecase.MonthPoint> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ToliqHisobotViewModel @Inject constructor(
    private val getDaily: GetDailyReportUseCase,
    private val getMonthly: GetMonthlyReportUseCase,
    private val getYearly: GetYearlyReportUseCase
) : ViewModel() {

    private val userId: Long = 1L

    private val _state = MutableStateFlow(ToliqState())
    val state: StateFlow<ToliqState> = _state.asStateFlow()

    init { load() }

    fun setMode(m: Int) {
        if (_state.value.mode == m) return
        _state.update { it.copy(mode = m, selectedDate = LocalDate.now()) }
        load()
    }

    /** Oldingi/keyingi davr: Bugun→kun, Shu oy→oy, Yil/Sof foyda→yil */
    fun step(delta: Int) {
        val cur = _state.value.selectedDate
        val next = when (_state.value.mode) {
            0 -> cur.plusDays(delta.toLong())
            2, 3 -> cur.plusYears(delta.toLong())
            else -> cur.plusMonths(delta.toLong())
        }
        _state.update { it.copy(selectedDate = next) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val d = _state.value.selectedDate
                if (_state.value.mode == 3) {
                    // 12 oy sof foyda (tanlangan yil) + yillik jami
                    val yr = getYearly(userId, d.year)
                    val months = (1..12).map { m ->
                        val mr = runCatching { getMonthly(userId, d.year, m) }.getOrNull()
                        uz.daftar.app.domain.usecase.MonthPoint(
                            label = MONTHS_UZ[m - 1], year = d.year, month = m,
                            revenue = mr?.revenue ?: 0L, profit = mr?.profit ?: 0L, payments = mr?.payments ?: 0L
                        )
                    }
                    _state.update { it.copy(isLoading = false, report = yr, monthlyProfits = months, error = null) }
                } else {
                    val r = when (_state.value.mode) {
                        0 -> getDaily(userId, d)
                        2 -> getYearly(userId, d.year)
                        else -> getMonthly(userId, d.year, d.monthValue)
                    }
                    _state.update { it.copy(isLoading = false, report = r, monthlyProfits = emptyList(), error = null) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private companion object {
        val MONTHS_UZ = listOf("Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
            "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr")
    }
}
