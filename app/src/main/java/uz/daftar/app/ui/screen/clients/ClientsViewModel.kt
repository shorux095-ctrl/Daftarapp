package uz.daftar.app.ui.screen.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.domain.usecase.ClientSummary
import uz.daftar.app.domain.usecase.GetAllClientsUseCase
import javax.inject.Inject

data class ClientsState(
    val isLoading: Boolean = true,
    val clients: List<ClientSummary> = emptyList(),
    val filter: String = "",
    val error: String? = null
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
    private val getAll: GetAllClientsUseCase
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
                _state.update { it.copy(isLoading = false, clients = data, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onFilterChange(text: String) {
        _state.update { it.copy(filter = text) }
    }
}
