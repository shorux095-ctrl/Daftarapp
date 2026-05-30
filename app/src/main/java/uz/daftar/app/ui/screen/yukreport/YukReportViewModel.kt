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
import uz.daftar.app.domain.usecase.YukReport
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class YukReportState(
    val isLoading: Boolean = true,
    val yearly: Boolean = false,
    val month: YearMonth = YearMonth.now(),
    val year: Int = LocalDate.now().year,
    val report: YukReport? = null
)

@HiltViewModel
class YukReportViewModel @Inject constructor(
    private val getReport: GetYukReportUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val cache = mutableMapOf<String, YukReport>()

    private val _state = MutableStateFlow(YukReportState())
    val state: StateFlow<YukReportState> = _state.asStateFlow()

    private var job: Job? = null

    init { load() }

    private fun load() {
        val s = _state.value
        val key = if (s.yearly) "y${s.year}" else "m${s.month}"
        // Cache'дa bo'lsa — darrov ko'rsatamiz (tez, qotmaydi)
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
            // Faqat hozirgi tanlov hali ham shu bo'lsa yangilaymiz
            val now = _state.value
            val stillSame = if (now.yearly) (now.yearly == s.yearly && now.year == s.year)
                            else (now.yearly == s.yearly && now.month == s.month)
            if (stillSame) {
                _state.update { it.copy(report = r, isLoading = false) }
            }
        }
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
}
