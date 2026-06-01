package uz.daftar.app.ui.screen.yuknarx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.entity.YukNarxEntity
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.domain.usecase.GetCurrentYukNarxUseCase
import uz.daftar.app.domain.usecase.SetYukNarxUseCase
import javax.inject.Inject

data class YukNarxState(
    val group: String = "t",                       // "t" yoki "t1"
    val current: Map<TxType, YukNarxEntity?> = emptyMap(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class YukNarxViewModel @Inject constructor(
    private val getCurrent: GetCurrentYukNarxUseCase,
    private val setNarx: SetYukNarxUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(YukNarxState())
    val state: StateFlow<YukNarxState> = _state.asStateFlow()

    init { refresh() }

    fun setGroup(group: String) {
        if (group == _state.value.group) return
        _state.update { it.copy(group = group) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val current = getCurrent(userId, _state.value.group)
            _state.update { it.copy(current = current, isLoading = false) }
        }
    }

    fun setPrices(input: Map<TxType, String>) {
        val parsed: Map<TxType, Double> = input.mapNotNull { (type, str) ->
            val v = str.trim().replace(",", ".").toDoubleOrNull()
            if (v != null && v > 0) type to v else null
        }.toMap()

        if (parsed.isEmpty()) {
            _state.update { it.copy(message = "❌ Hech qanday narx kiritilmadi") }
            return
        }
        val group = _state.value.group
        viewModelScope.launch {
            setNarx(userId, parsed, group)
            _state.update {
                it.copy(message = "✅ ${parsed.size} ta ${group.uppercase()} narx saqlandi")
            }
            refresh()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
