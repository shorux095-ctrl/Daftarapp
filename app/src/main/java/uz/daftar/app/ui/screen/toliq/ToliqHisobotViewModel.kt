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
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.domain.usecase.PeriodReport
import java.time.LocalDate
import javax.inject.Inject

/** 🏆 TOP statistika: eng ko'p qarzdor, eng ko'p yuk olgan, eng ko'p sotilgan tur */
data class TopStats(
    val topDebtor: String? = null, val topDebtorSum: Long = 0,
    val topClient: String? = null, val topClientQty: Double = 0.0,
    val topType: String? = null, val topTypeQty: Double = 0.0
)

data class ToliqState(
    val isLoading: Boolean = true,
    val mode: Int = 0,                 // 0 = Bugun, 1 = Shu oy, 2 = Yil, 3 = Sof foyda (12 oy)
    val selectedDate: LocalDate = LocalDate.now(),
    val report: PeriodReport? = null,
    val monthlyProfits: List<uz.daftar.app.domain.usecase.MonthPoint> = emptyList(),
    val stats: TopStats? = null,
    val error: String? = null
)

@HiltViewModel
class ToliqHisobotViewModel @Inject constructor(
    private val getDaily: GetDailyReportUseCase,
    private val getMonthly: GetMonthlyReportUseCase,
    private val getYearly: GetYearlyReportUseCase,
    private val getOverdue: uz.daftar.app.domain.usecase.GetOverdueDebtorsUseCase,
    private val txDao: uz.daftar.app.data.db.dao.TransactionDao
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
                    _state.update { it.copy(isLoading = false, report = yr, monthlyProfits = months, stats = computeStats(d, 3, yr), error = null) }
                } else {
                    val r = when (_state.value.mode) {
                        0 -> getDaily(userId, d)
                        2 -> getYearly(userId, d.year)
                        else -> getMonthly(userId, d.year, d.monthValue)
                    }
                    _state.update { it.copy(isLoading = false, report = r, monthlyProfits = emptyList(), stats = computeStats(d, _state.value.mode, r), error = null) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** 🏆 TOP statistika: eng ko'p qarzdor (jami), eng ko'p yuk olgan (davr), eng ko'p sotilgan tur (davr) */
    private suspend fun computeStats(d: LocalDate, mode: Int, report: PeriodReport): TopStats {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val (startD, endEx) = when (mode) {
            0 -> d to d.plusDays(1)
            1 -> d.withDayOfMonth(1) to d.withDayOfMonth(1).plusMonths(1)
            else -> LocalDate.of(d.year, 1, 1) to LocalDate.of(d.year + 1, 1, 1)
        }
        val from = startD.atStartOfDay().format(fmt)
        val to = endEx.atStartOfDay().minusSeconds(1).format(fmt)
        val topClient = runCatching { txDao.topCargoClient(userId, from, to) }.getOrNull()
        val debtors = runCatching { getOverdue(userId) }.getOrDefault(emptyList())
        val topDebtor = debtors.maxByOrNull { it.debt }
        val cargo = setOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
        val topType = report.totals.filterKeys { it in cargo }.maxByOrNull { it.value }
        return TopStats(
            topDebtor = topDebtor?.client, topDebtorSum = topDebtor?.debt ?: 0L,
            topClient = topClient?.clientName, topClientQty = topClient?.total ?: 0.0,
            topType = topType?.key?.name, topTypeQty = topType?.value ?: 0.0
        )
    }

    private companion object {
        val MONTHS_UZ = listOf("Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
            "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr")
    }
}
