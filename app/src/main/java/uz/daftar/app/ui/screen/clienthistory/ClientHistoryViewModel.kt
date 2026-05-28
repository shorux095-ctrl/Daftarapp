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
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.domain.usecase.DeleteTransactionUseCase
import uz.daftar.app.domain.usecase.GetClientHistoryUseCase
import uz.daftar.app.domain.usecase.GetClientUnitPricesUseCase
import java.time.YearMonth
import javax.inject.Inject

data class ClientHistoryState(
    val isLoading: Boolean = true,
    val clientName: String = "",
    val debt: Long = 0,
    val transactions: List<TransactionEntity> = emptyList(),
    val unitPrices: Map<TxType, Double> = emptyMap(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val error: String? = null
)

@HiltViewModel
class ClientHistoryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val getHistory: GetClientHistoryUseCase,
    private val getUnitPrices: GetClientUnitPricesUseCase,
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
                val prices = runCatching { getUnitPrices(userId, clientName) }.getOrDefault(emptyMap())
                _state.update {
                    it.copy(
                        isLoading = false,
                        debt = h.debt,
                        transactions = h.transactions.sortedByDescending { tx -> tx.date },
                        unitPrices = prices
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
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
