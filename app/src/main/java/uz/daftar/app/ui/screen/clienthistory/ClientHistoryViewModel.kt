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
import uz.daftar.app.domain.usecase.DeleteTransactionUseCase
import uz.daftar.app.domain.usecase.GetClientHistoryUseCase
import javax.inject.Inject

data class ClientHistoryState(
    val isLoading: Boolean = true,
    val clientName: String = "",
    val debt: Long = 0,
    val transactions: List<TransactionEntity> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ClientHistoryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val getHistory: GetClientHistoryUseCase,
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
                _state.update {
                    it.copy(
                        isLoading = false,
                        debt = h.debt,
                        transactions = h.transactions.sortedByDescending { tx -> tx.date }
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            try {
                deleteTx(id)
                load()  // qayta yuklash
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}
