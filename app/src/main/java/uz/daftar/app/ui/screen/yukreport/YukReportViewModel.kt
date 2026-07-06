package uz.daftar.app.ui.screen.yukreport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.daftar.app.domain.usecase.GetYukReportUseCase
import uz.daftar.app.domain.usecase.GetDateReportUseCase
import uz.daftar.app.domain.usecase.DateReport
import uz.daftar.app.domain.usecase.YukReport
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class YukReportState(
    val isLoading: Boolean = true,
    val yearly: Boolean = false,
    val counts: Boolean = false,
    val month: YearMonth = YearMonth.now(),
    val year: Int = LocalDate.now().year,
    val report: YukReport? = null,
    val countReport: uz.daftar.app.domain.usecase.YukCountReport? = null,
    // v143: jadval katagi bosilganda — o'sha KUN tafsiloti (T/N/P filtri bilan)
    val dayDetail: DateReport? = null,
    val dayDetailTitle: String = "",
    val dayDetailLoading: Boolean = false
)

@HiltViewModel
class YukReportViewModel @Inject constructor(
    private val getReport: GetYukReportUseCase,
    private val getDateReport: GetDateReportUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val cache = mutableMapOf<String, YukReport>()
    private val countCache = mutableMapOf<String, uz.daftar.app.domain.usecase.YukCountReport>()

    private val _state = MutableStateFlow(YukReportState())
    val state: StateFlow<YukReportState> = _state.asStateFlow()

    private var job: Job? = null

    init { load() }

    private fun load() {
        val s = _state.value
        if (s.counts) {
            val key = if (s.yearly) "y${s.year}" else "m${s.month}"
            countCache[key]?.let { cached ->
                _state.update { it.copy(countReport = cached, isLoading = false) }
                return
            }
            job?.cancel()
            job = viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val r = withContext(Dispatchers.Default) {
                    if (s.yearly) getReport.countsYearly(userId, s.year)
                    else getReport.countsMonthly(userId, s.month)
                }
                countCache[key] = r
                val now = _state.value
                if (now.counts == s.counts && now.yearly == s.yearly &&
                    (if (s.yearly) now.year == s.year else now.month == s.month)) {
                    _state.update { it.copy(countReport = r, isLoading = false) }
                }
            }
            return
        }
        val key = if (s.yearly) "y${s.year}" else "m${s.month}"
        cache[key]?.let { cached ->
            _state.update { it.copy(report = cached, isLoading = false) }
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val r = withContext(Dispatchers.Default) {
                if (s.yearly) getReport.yearly(userId, s.year)
                else getReport.monthly(userId, s.month)
            }
            cache[key] = r
            val now = _state.value
            if (!now.counts && now.yearly == s.yearly &&
                (if (s.yearly) now.year == s.year else now.month == s.month)) {
                _state.update { it.copy(report = r, isLoading = false) }
            }
        }
    }

    fun showMoney() {
        if (!_state.value.counts) return
        _state.update { it.copy(counts = false) }
        load()
    }

    fun showCounts() {
        if (_state.value.counts) return
        _state.update { it.copy(counts = true) }
        load()
    }

    fun prev() {
        _state.update { if (it.yearly) it.copy(year = it.year - 1) else it.copy(month = it.month.minusMonths(1)) }
        load()
    }

    fun next() {
        _state.update { if (it.yearly) it.copy(year = it.year + 1) else it.copy(month = it.month.plusMonths(1)) }
        load()
    }

    fun showYearly() {
        if (_state.value.yearly) return
        _state.update { it.copy(yearly = true) }
        load()
    }

    fun showMonthly() {
        if (!_state.value.yearly) return
        _state.update { it.copy(yearly = false) }
        load()
    }

    /**
     * v143: jadval katagi bosildi.
     * mode: 't' = tannarx hisobot, 'n' = narx (sotish) hisobot, 'p' = faqat pullar, 'a' = to'liq (sana bosilganda)
     */
    fun openDay(label: String, mode: Char) {
        val s = _state.value
        if (s.yearly) return  // yillikda qator = oy, kun emas
        val m = Regex("""^(\d{1,2})\.(\d{1,2})$""").matchEntire(label.trim()) ?: return
        val date = runCatching {
            LocalDate.of(s.month.year, m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }.getOrNull() ?: return
        val title = when (mode) {
            'p' -> "💵 $label — Pullar"
            'n' -> "💰 $label — Narx (sotish)"
            't' -> "📦 $label — Tannarx"
            else -> "📅 $label — To'liq hisobot"
        }
        viewModelScope.launch {
            _state.update { it.copy(dayDetailLoading = true, dayDetailTitle = title, dayDetail = null) }
            val r = runCatching {
                withContext(Dispatchers.Default) {
                    when (mode) {
                        'p' -> getDateReport(userId, date, setOf("p"), false)
                        'n' -> getDateReport(userId, date, null, true)
                        else -> getDateReport(userId, date, null, false)
                    }
                }
            }.getOrNull()
            _state.update { it.copy(dayDetail = r, dayDetailLoading = false) }
        }
    }

    fun closeDay() {
        _state.update { it.copy(dayDetail = null, dayDetailLoading = false) }
    }
}
