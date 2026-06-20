package uz.daftar.app.ui.screen.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.PriceHistoryEntity
import java.time.LocalDate
import javax.inject.Inject

data class CargoBit(val type: String, val qty: Double, val price: Double?)

data class DayClientLine(
    val clientName: String,
    val cargo: List<CargoBit>,
    val payment: Double,
    val manualDebt: Double
)

data class DailyReportState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val lines: List<DayClientLine> = emptyList(),
    val totalsByType: List<Pair<String, Double>> = emptyList(),
    val totalPayment: Double = 0.0,
    val totalCargoValue: Double = 0.0,
    val totalManualDebt: Double = 0.0,
    val clientCount: Int = 0,
    val error: String? = null
)

@HiltViewModel
class DailyReportViewModel @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao
) : ViewModel() {

    private val userId = 1L
    private val _state = MutableStateFlow(DailyReportState())
    val state: StateFlow<DailyReportState> = _state.asStateFlow()

    init { load() }

    fun setDate(d: LocalDate) { _state.update { it.copy(date = d) }; load() }
    fun prevDay() { _state.update { it.copy(date = it.date.minusDays(1)) }; load() }
    fun nextDay() { _state.update { it.copy(date = it.date.plusDays(1)) }; load() }

    fun load() {
        val d = _state.value.date
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val start = "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)
                val nx = d.plusDays(1)
                val end = "%04d-%02d-%02d".format(nx.year, nx.monthValue, nx.dayOfMonth)
                val txs = txDao.getRange(userId, start, end)

                val allPrices = priceDao.getAllForUser(userId)
                val priceMap: Map<Pair<String, String>, List<PriceHistoryEntity>> =
                    allPrices.groupBy { it.clientName.lowercase() to it.priceType.lowercase() }

                fun priceAt(client: String, type: String, date: String): Double? {
                    val list = priceMap[client.lowercase() to type.lowercase()] ?: return null
                    var best: PriceHistoryEntity? = null
                    for (p in list.sortedBy { it.date }) { if (p.date <= date) best = p else break }
                    return best?.price ?: list.minByOrNull { it.date }?.price
                }

                val cargoTypes = setOf("a", "b", "c", "d", "k")
                val byClient = txs.groupBy { it.clientName }
                val lines = byClient.map { (client, list) ->
                    val cargo = list.filter { it.type.lowercase() in cargoTypes }
                        .map { CargoBit(it.type.uppercase(), it.amount, priceAt(client, it.type, it.date)) }
                    val pay = list.filter { it.type.equals("p", true) }.sumOf { it.amount }
                    val q = list.filter { it.type.equals("q", true) }.sumOf { it.amount }
                    DayClientLine(client, cargo, pay, q)
                }

                val totByType = linkedMapOf<String, Double>()
                var cargoValue = 0.0
                for (ln in lines) {
                    for (c in ln.cargo) {
                        totByType[c.type] = (totByType[c.type] ?: 0.0) + c.qty
                        if (c.price != null) cargoValue += c.qty * c.price
                    }
                }
                val totPay = lines.sumOf { it.payment }
                val totQ = lines.sumOf { it.manualDebt }

                _state.update {
                    it.copy(
                        isLoading = false,
                        lines = lines,
                        totalsByType = totByType.toList(),
                        totalPayment = totPay,
                        totalCargoValue = cargoValue,
                        totalManualDebt = totQ,
                        clientCount = lines.size,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
