package uz.daftar.app.ui.screen.rasxod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.dao.YukNarxDao
import uz.daftar.app.data.db.entity.RasxodEntity
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.domain.usecase.AddRasxodUseCase
import uz.daftar.app.domain.usecase.DeleteRasxodUseCase
import uz.daftar.app.domain.usecase.GetRasxodRangeUseCase
import uz.daftar.app.domain.usecase.GetRasxodTotalUseCase
import uz.daftar.app.domain.usecase.SetGlobalPriceUseCase
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToLong

enum class RasxodPeriod { DAY, MONTH, YEAR }

/** Yuk rasxodi bitta turi: A: qty × rate = cost */
data class YukLine(val type: String, val qty: Double, val rate: Double, val cost: Long)

data class RasxodState(
    val period: RasxodPeriod = RasxodPeriod.DAY,
    val anchor: LocalDate = LocalDate.now(),
    val items: List<RasxodEntity> = emptyList(),
    val total: Long = 0,
    val label: String = "",
    val isLoading: Boolean = true,
    val message: String? = null,
    // Yuk rasxodi
    val yukRateInputs: Map<String, String> = emptyMap(),  // joriy narxlar (input uchun)
    val yukBreakdown: List<YukLine> = emptyList(),         // tur bo'yicha hisob
    val yukTotal: Long = 0                                  // jami yuk rasxodi
)

@HiltViewModel
class RasxodViewModel @Inject constructor(
    private val addUC: AddRasxodUseCase,
    private val deleteUC: DeleteRasxodUseCase,
    private val rangeUC: GetRasxodRangeUseCase,
    private val totalUC: GetRasxodTotalUseCase,
    private val txDao: TransactionDao,
    private val yukDao: YukNarxDao,
    private val setGlobalUC: SetGlobalPriceUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(RasxodState())
    val state: StateFlow<RasxodState> = _state.asStateFlow()

    private val CARGO = listOf("a", "b", "c", "d", "k")

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

    private fun fmtNum(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

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

            // ── Yuk rasxodi: tur bo'yicha (sotilgan miqdor × joriy narx) ──
            val startStr = from.atStartOfDay().format(ISO)
            val endStr = to.plusDays(1).atStartOfDay().format(ISO)
            val txs = txDao.getRange(userId, startStr, endStr)
            val qtyByType = HashMap<String, Double>()
            for (tx in txs) {
                val code = tx.type.lowercase()
                if (code in CARGO) qtyByType[code] = (qtyByType[code] ?: 0.0) + tx.amount
            }
            val rateByType = HashMap<String, Double>()
            for (t in CARGO) rateByType[t] = yukDao.getLatestGlobal(userId, t, "yr")?.price ?: 0.0

            // Sanali narx: har yozuv sanasiga qarab (narx yo'q = 0 → eski davr o'zgarmaydi)
            val yrAll = yukDao.getAllGlobalGroup(userId, "yr").groupBy { it.type.lowercase() }
            fun yrRateAt(code: String, date: String): Double {
                val list = (yrAll[code] ?: emptyList()).map { it.date to it.price }.sortedBy { it.first }
                var best = 0.0; var found = false
                for ((d, p) in list) { if (d.take(10) <= date.take(10)) { best = p; found = true } else break }
                return if (found) best else 0.0
            }
            val costByType = HashMap<String, Long>()
            for (tx in txs) {
                val code = tx.type.lowercase()
                if (code in CARGO) costByType[code] = (costByType[code] ?: 0L) + (tx.amount * yrRateAt(code, tx.date)).roundToLong()
            }
            val periodEnd = to.atTime(23, 59, 59).format(ISO)

            val breakdown = CARGO.mapNotNull { t ->
                val qty = qtyByType[t] ?: 0.0
                if (qty == 0.0) return@mapNotNull null
                YukLine(t.uppercase(), qty, yrRateAt(t, periodEnd), costByType[t] ?: 0L)
            }
            val yukTot = breakdown.sumOf { it.cost }
            val rateInputs = CARGO.associateWith { t ->
                val r = yrRateAt(t, periodEnd)
                if (r > 0.0) fmtNum(r) else ""
            }

            _state.update {
                it.copy(
                    items = items, total = total, label = label, isLoading = false,
                    yukBreakdown = breakdown, yukTotal = yukTot, yukRateInputs = rateInputs
                )
            }
        }
    }

    /** Yuk rasxodi narxlarini saqlash — BUGUNGI sanadan boshlab (eski hisobot o'zgarmaydi) */
    fun saveYukRates(inputs: Map<String, String>) {
        val prices = inputs.mapNotNull { (t, str) ->
            val v = str.trim().replace(",", ".").toDoubleOrNull()
            if (v != null && v >= 0.0) t.lowercase() to v else null
        }.toMap()
        if (prices.isEmpty()) {
            _state.update { it.copy(message = "❌ Narx kiritilmadi") }
            return
        }
        viewModelScope.launch {
            setGlobalUC(userId, "yr", prices, LocalDate.now())
            _state.update { it.copy(message = "✅ Yuk rasxodi narxi saqlandi (bugundan)") }
            load()
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
        private val ISO = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
