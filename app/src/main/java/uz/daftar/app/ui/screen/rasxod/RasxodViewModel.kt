package uz.daftar.app.ui.screen.rasxod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.entity.RasxodEntity
import uz.daftar.app.domain.usecase.AddRasxodUseCase
import uz.daftar.app.domain.usecase.DeleteRasxodUseCase
import uz.daftar.app.domain.usecase.GetRasxodRangeUseCase
import uz.daftar.app.domain.usecase.GetRasxodTotalUseCase
import java.time.LocalDate
import javax.inject.Inject

enum class RasxodPeriod { DAY, MONTH, YEAR }

data class RasxodState(
    val period: RasxodPeriod = RasxodPeriod.DAY,
    val anchor: LocalDate = LocalDate.now(),
    val items: List<RasxodEntity> = emptyList(),
    val total: Long = 0,
    val label: String = "",
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class RasxodViewModel @Inject constructor(
    private val addUC: AddRasxodUseCase,
    private val deleteUC: DeleteRasxodUseCase,
    private val rangeUC: GetRasxodRangeUseCase,
    private val totalUC: GetRasxodTotalUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(RasxodState())
    val state: StateFlow<RasxodState> = _state.asStateFlow()

    init { load() }

    fun setPeriod(p: RasxodPeriod) {
        _state.update { it.copy(period = p, anchor = LocalDate.now()) }
        load()
    }

    fun prev() {
        _state.update { it.copy(anchor = shift(it.anchor, it.period, -1)) }
        load()
    }

    fun next() {
        _state.update { it.copy(anchor = shift(it.anchor, it.period, +1)) }
        load()
    }

    private fun shift(d: LocalDate, p: RasxodPeriod, dir: Int): LocalDate = when (p) {
        RasxodPeriod.DAY -> d.plusDays(dir.toLong())
        RasxodPeriod.MONTH -> d.plusMonths(dir.toLong())
        RasxodPeriod.YEAR -> d.plusYears(dir.toLong())
    }

    fun load() {
        val s = state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val (from, to, label) = when (s.period) {
                RasxodPeriod.DAY -> Triple(s.anchor, s.anchor, s.anchor.format(FMT_DAY))
                RasxodPeriod.MONTH -> Triple(
                    s.anchor.withDayOfMonth(1),
                    s.anchor.withDayOfMonth(s.anchor.lengthOfMonth()),
                    s.anchor.format(FMT_MONTH)
                )
                RasxodPeriod.YEAR -> Triple(
                    s.anchor.withDayOfYear(1),
                    s.anchor.withMonth(12).withDayOfMonth(31),
                    s.anchor.year.toString()
                )
            }
            val items = rangeUC(userId, from, to).sortedByDescending { it.date }
            val total = totalUC(userId, from, to)
            _state.update { it.copy(items = items, total = total, label = label, isLoading = false) }
        }
    }

    fun add(amountStr: String, note: String) {
        val amount = amountStr.trim().replace(",", ".").toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _state.update { it.copy(message = "❌ Noto'g'ri summa") }
            return
        }
        viewModelScope.launch {
            addUC(userId, amount, note.trim())
            _state.update { it.copy(message = "✅ Rasxod qo'shildi") }
            // Yangi rasxod bugun, shuning uchun Kunga qaytamiz
            _state.update { it.copy(period = RasxodPeriod.DAY, anchor = LocalDate.now()) }
            load()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            deleteUC(id)
            _state.update { it.copy(message = "🗑 O'chirildi") }
            load()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        private val FMT_DAY = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val FMT_MONTH = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
