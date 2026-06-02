package uz.daftar.app.ui.screen.newtx

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
import uz.daftar.app.core.parser.ParsedEntry
import uz.daftar.app.domain.usecase.AddTransactionUseCase
import javax.inject.Inject

data class NewTxState(
    val input: String = "",
    val preview: ParsedEntry? = null,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val savedSummary: String? = null
)

@HiltViewModel
class NewTransactionViewModel @Inject constructor(
    private val addTx: AddTransactionUseCase
) : ViewModel() {

    private val userId: Long = 1L

    private val _state = MutableStateFlow(NewTxState())
    val state: StateFlow<NewTxState> = _state.asStateFlow()

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text, isSaved = false, savedSummary = null) }
        // Realtime parser preview
        if (text.isBlank()) {
            _state.update { it.copy(preview = null, errorMessage = null) }
            return
        }
        when (val r = DaftarParser.parse(text)) {
            is ParseResult.Success -> _state.update { it.copy(preview = r.entry, errorMessage = null) }
            is ParseResult.Failure -> _state.update { it.copy(preview = null, errorMessage = r.error.message) }
        }
    }

    fun save() {
        val s = state.value
        val entry = s.preview ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val result = addTx(userId, entry)
                val summary = buildString {
                    append("✅ Saqlandi: ${entry.clientName.replaceFirstChar { it.titlecase() }}")
                    if (result.txCount > 0) append("  •  ${result.txCount} ta yozuv")
                    if (result.nPriceCount > 0) append("  •  N narx: ${result.nPriceCount}")
                    if (result.tPriceCount > 0) append("  •  T narx: ${result.tPriceCount}")
                    if (result.tOneTimeCount > 0) append("  •  T?: ${result.tOneTimeCount}")
                }
                _state.update {
                    it.copy(
                        isSaving = false,
                        isSaved = true,
                        savedSummary = summary,
                        input = "",
                        preview = null,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = "Xato: ${e.message}") }
            }
        }
    }

    fun clearSaved() {
        _state.update { it.copy(isSaved = false, savedSummary = null) }
    }
}
