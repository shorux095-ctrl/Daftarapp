package uz.daftar.app.ui.screen.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.core.parser.DaftarParser
import uz.daftar.app.core.parser.ParseResult
import uz.daftar.app.domain.usecase.AddTransactionUseCase
import uz.daftar.app.domain.usecase.ClientSummary
import uz.daftar.app.domain.usecase.GetAllClientsUseCase
import javax.inject.Inject

data class ClientsState(
    val isLoading: Boolean = true,
    val clients: List<ClientSummary> = emptyList(),
    val filter: String = "",
    val error: String? = null,
    val info: String? = null
) {
    val filtered: List<ClientSummary>
        get() {
            val f = filter.trim().lowercase()
            return if (f.isEmpty()) clients
            else clients.filter { it.name.contains(f) }
        }
}

@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val getAll: GetAllClientsUseCase,
    private val addTx: AddTransactionUseCase
) : ViewModel() {

    private val userId: Long = 1L

    private val _state = MutableStateFlow(ClientsState())
    val state: StateFlow<ClientsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val data = getAll(userId)
                val sorted = data.sortedBy { it.name.trim().lowercase() }
                _state.update { it.copy(isLoading = false, clients = sorted, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onFilterChange(text: String) {
        _state.update { it.copy(filter = text) }
    }

    /** "ali 50000" — qarzdordan to'lov qabul qilish (P yozuvi) */
    fun addPayment(text: String) {
        val t = text.trim()
        val parts = t.split(Regex("\\s+"))
        if (parts.size < 2) { _state.update { it.copy(info = "Format: ism summa (masalan: ali 50000)") }; return }
        val amountStr = parts.last().replace(",", ".")
        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) { _state.update { it.copy(info = "Summa noto'g'ri: ${parts.last()}") }; return }
        val nameTyped = parts.dropLast(1).joinToString(" ").lowercase()
        // Faqat MAVJUD mijoz — xato yozsangiz yangi mijoz ochilib ketmasin
        val match = _state.value.clients.firstOrNull { it.name.equals(nameTyped, true) }
            ?: _state.value.clients.firstOrNull { it.name.startsWith(nameTyped) }
        if (match == null) { _state.update { it.copy(info = "Mijoz topilmadi: $nameTyped") }; return }
        viewModelScope.launch {
            when (val r = DaftarParser.parse("${match.name} p $amountStr")) {
                is ParseResult.Success -> {
                    runCatching { addTx(userId, r.entry) }
                        .onSuccess {
                            _state.update { it.copy(info = "\u2705 ${match.name.replaceFirstChar { c -> c.uppercase() }}: ${amountStr} to'lov saqlandi") }
                            load()
                        }
                        .onFailure { e -> _state.update { it.copy(info = "\u274c Xato: ${e.message}") } }
                }
                else -> _state.update { it.copy(info = "Tushunarsiz: $t") }
            }
        }
    }

    fun clearInfo() { _state.update { it.copy(info = null) } }
}
