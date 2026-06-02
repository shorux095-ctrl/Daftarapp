package uz.daftar.app.ui.screen.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.domain.usecase.EditTransactionUseCase
import javax.inject.Inject

data class EditState(
    val isLoading: Boolean = true,
    val original: TransactionEntity? = null,
    val type: TxType = TxType.A,
    val amount: String = "",
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val txDao: TransactionDao,
    private val editUC: EditTransactionUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val txId: Long = savedState.get<String>("txId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            try {
                // Bitta tx olish uchun barcha tx orasidan topamiz (kichik trade-off — alohida getById ham qo'shish mumkin)
                val all = txDao.getRange(userId, "0000-00-00 00:00:00", "9999-12-31 23:59:59")
                val tx = all.firstOrNull { it.id == txId }
                if (tx != null) {
                    val type = TxType.fromCode(tx.type) ?: TxType.A
                    _state.update {
                        it.copy(
                            isLoading = false,
                            original = tx,
                            type = type,
                            amount = if (tx.amount == tx.amount.toLong().toDouble()) tx.amount.toLong().toString() else tx.amount.toString()
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, error = "Yozuv topilmadi") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setType(t: TxType) {
        _state.update { it.copy(type = t) }
    }

    fun setAmount(s: String) {
        _state.update { it.copy(amount = s) }
    }

    fun save() {
        val s = state.value
        val orig = s.original ?: return
        val amt = s.amount.replace(",", ".").toDoubleOrNull()
        if (amt == null || amt <= 0) {
            _state.update { it.copy(error = "Noto'g'ri summa") }
            return
        }
        viewModelScope.launch {
            try {
                editUC(
                    id = orig.id,
                    userId = orig.userId,
                    clientName = orig.clientName,
                    type = s.type,
                    amount = amt,
                    date = orig.date,
                    tOverride = orig.tOverride
                )
                _state.update { it.copy(isSaved = true, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}
