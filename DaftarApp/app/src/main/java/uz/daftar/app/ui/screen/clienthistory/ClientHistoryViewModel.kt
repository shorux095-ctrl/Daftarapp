package uz.daftar.app.ui.screen.clienthistory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.entity.PriceHistoryEntity
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.domain.usecase.DeleteTransactionUseCase
import uz.daftar.app.domain.usecase.GetClientHistoryUseCase
import java.time.YearMonth
import javax.inject.Inject

data class ClientHistoryState(
    val isLoading: Boolean = true,
    val clientName: String = "",
    val debt: Long = 0,
    val transactions: List<TransactionEntity> = emptyList(),
    /** Har tx uchun ayni o'sha sanada narx (tarixiy) — [4.5] uchun */
    val priceByTx: Map<Long, Double?> = emptyMap(),
    /** Har P (to'lov) keyingi qoldiq qarz: txId -> qoldiq (manfiy = ortiqcha) */
    val balanceAfterPayment: Map<Long, Long> = emptyMap(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val error: String? = null
)

@HiltViewModel
class ClientHistoryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val getHistory: GetClientHistoryUseCase,
    private val priceDao: PriceHistoryDao,
    private val deleteTx: DeleteTransactionUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val clientName: String = savedState.get<String>("clientName") ?: ""

    private val _state = MutableStateFlow(ClientHistoryState(clientName = clientName))
    val state: StateFlow<ClientHistoryState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val h = getHistory(userId, clientName)
                val allPrices = priceDao.getAllForClient(userId, clientName.lowercase())
                val pricesByType: Map<String, List<PriceHistoryEntity>> = allPrices.groupBy { it.priceType }

                // Per-tx narx (tarixiy)
                val priceByTx = mutableMapOf<Long, Double?>()
                for (tx in h.transactions) {
                    priceByTx[tx.id] = tx.tOverride ?: findPriceAtDate(pricesByType[tx.type], tx.date)
                }

                // Running debt — payment'дan keyingi qoldiqни hisoblash
                // Eski → yangi tartibda iteratsiya
                val asc = h.transactions.sortedBy { it.date }
                var running = 0.0
                val balAfterPay = mutableMapOf<Long, Long>()
                for (tx in asc) {
                    val type = tx.type.lowercase()
                    when (type) {
                        "p" -> {
                            running -= tx.amount
                            balAfterPay[tx.id] = running.toLong()
                        }
                        "q" -> running += tx.amount
                        else -> {
                            val p = priceByTx[tx.id]
                            if (p != null) running += tx.amount * p
                        }
                    }
                }

                val latestMonth = h.transactions.maxByOrNull { it.date }?.date?.let { d ->
                    if (d.length >= 7) runCatching { YearMonth.parse(d.take(7)) }.getOrNull() else null
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        debt = h.debt,
                        transactions = h.transactions.sortedByDescending { tx -> tx.date },
                        priceByTx = priceByTx,
                        balanceAfterPayment = balAfterPay,
                        selectedMonth = latestMonth ?: it.selectedMonth
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun findPriceAtDate(prices: List<PriceHistoryEntity>?, atDate: String): Double? {
        if (prices.isNullOrEmpty()) return null
        var best: PriceHistoryEntity? = null
        for (p in prices.sortedBy { it.date }) {
            if (p.date <= atDate) best = p else break
        }
        if (best != null) return best.price
        return prices.minByOrNull { it.date }?.price  // retroaktiv
    }

    fun prevMonth() {
        _state.update { it.copy(selectedMonth = it.selectedMonth.minusMonths(1)) }
    }

    fun nextMonth() {
        _state.update { it.copy(selectedMonth = it.selectedMonth.plusMonths(1)) }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            try {
                deleteTx(id)
                load()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}
