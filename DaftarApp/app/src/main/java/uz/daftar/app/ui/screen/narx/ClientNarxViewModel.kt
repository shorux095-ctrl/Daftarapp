package uz.daftar.app.ui.screen.narx

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.domain.usecase.GetClientNarxUseCase
import uz.daftar.app.domain.usecase.SetClientNarxUseCase
import java.net.URLDecoder
import javax.inject.Inject

data class ClientNarxState(
    val clientName: String = "",
    val current: Map<TxType, Double?> = emptyMap(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class ClientNarxViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val getNarx: GetClientNarxUseCase,
    private val setNarx: SetClientNarxUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val clientName: String = runCatching {
        URLDecoder.decode(savedState.get<String>("clientName") ?: "", "UTF-8")
    }.getOrDefault(savedState.get<String>("clientName") ?: "")

    private val _state = MutableStateFlow(ClientNarxState(clientName = clientName))
    val state: StateFlow<ClientNarxState> = _state.asStateFlow()

    init { refresh() }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val current = getNarx(userId, clientName)
            _state.update { it.copy(current = current, isLoading = false) }
        }
    }

    fun save(input: Map<TxType, String>) {
        val parsed: Map<TxType, Double> = input.mapNotNull { (type, str) ->
            val v = str.trim().replace(",", ".").toDoubleOrNull()
            if (v != null && v > 0) type to v else null
        }.toMap()

        if (parsed.isEmpty()) {
            _state.update { it.copy(message = "❌ Hech qanday narx kiritilmadi") }
            return
        }
        viewModelScope.launch {
            setNarx(userId, clientName, parsed)
            _state.update { it.copy(message = "✅ ${parsed.size} ta N narx saqlandi") }
            refresh()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
