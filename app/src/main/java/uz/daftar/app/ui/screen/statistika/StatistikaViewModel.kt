package uz.daftar.app.ui.screen.statistika

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.domain.usecase.GetOverdueDebtorsUseCase
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToLong

data class StatItem(val name: String, val count: Int)

data class StatistikaState(
    val totalClients: Int = 0,
    val debtorCount: Int = 0,
    val totalDebt: Long = 0,
    val avgDebt: Long = 0,
    val totalTx: Int = 0,
    val cargoCount: Int = 0,
    val totalPayments: Long = 0,
    val totalManualDebt: Long = 0,
    val topClients: List<StatItem> = emptyList(),
    val busiestDay: String = "—",
    val firstDate: String = "",
    val loading: Boolean = true
)

@HiltViewModel
class StatistikaViewModel @Inject constructor(
    private val txDao: TransactionDao,
    private val getOverdue: GetOverdueDebtorsUseCase
) : ViewModel() {

    private val userId = 1L
    private val _state = MutableStateFlow(StatistikaState())
    val state: StateFlow<StatistikaState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val txs = runCatching {
                txDao.getRange(userId, "2000-01-01", today.plusDays(1).toString())
            }.getOrDefault(emptyList())
            val names = runCatching { txDao.getAllClientNames(userId) }.getOrDefault(emptyList())
            val overdue = runCatching { getOverdue(userId) }.getOrDefault(emptyList())

            val cargoSet = setOf("a", "b", "c", "d", "k")
            val cargo = txs.filter { it.type.lowercase() in cargoSet }
            val payments = txs.filter { it.type.equals("p", true) }
            val manualDebt = txs.filter { it.type.equals("q", true) }

            val top = cargo.groupingBy { it.clientName }.eachCount()
                .entries.sortedByDescending { it.value }.take(5)
                .map { StatItem(it.key, it.value) }

            val dayNames = listOf("Yakshanba", "Dushanba", "Seshanba", "Chorshanba", "Payshanba", "Juma", "Shanba")
            val byDay = txs.mapNotNull { tx ->
                runCatching { LocalDate.parse(tx.date.take(10)).dayOfWeek.value % 7 }.getOrNull()
            }.groupingBy { it }.eachCount()
            val busiest = byDay.maxByOrNull { it.value }?.key?.let { dayNames.getOrElse(it) { "—" } } ?: "—"

            val totalDebt = overdue.sumOf { it.debt }
            val firstDate = txs.minByOrNull { it.date }?.date?.take(10) ?: ""

            _state.update {
                StatistikaState(
                    totalClients = names.size,
                    debtorCount = overdue.size,
                    totalDebt = totalDebt,
                    avgDebt = if (overdue.isNotEmpty()) totalDebt / overdue.size else 0L,
                    totalTx = txs.size,
                    cargoCount = cargo.size,
                    totalPayments = payments.sumOf { it.amount }.roundToLong(),
                    totalManualDebt = manualDebt.sumOf { it.amount }.roundToLong(),
                    topClients = top,
                    busiestDay = busiest,
                    firstDate = firstDate,
                    loading = false
                )
            }
        }
    }
}
