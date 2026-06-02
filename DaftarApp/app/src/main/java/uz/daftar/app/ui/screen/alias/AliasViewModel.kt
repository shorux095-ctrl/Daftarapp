package uz.daftar.app.ui.screen.alias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.entity.AliasEntity
import uz.daftar.app.domain.usecase.AddAliasUseCase
import uz.daftar.app.domain.usecase.DeleteAliasUseCase
import uz.daftar.app.domain.usecase.GetAliasesUseCase
import uz.daftar.app.domain.usecase.RenameClientUseCase
import javax.inject.Inject

data class AliasState(
    val aliases: List<AliasEntity> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class AliasViewModel @Inject constructor(
    private val getAliases: GetAliasesUseCase,
    private val addAlias: AddAliasUseCase,
    private val deleteAlias: DeleteAliasUseCase,
    private val rename: RenameClientUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(AliasState())
    val state: StateFlow<AliasState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val list = getAliases(userId)
            _state.update { it.copy(aliases = list, isLoading = false) }
        }
    }

    fun addAliasAction(from: String, to: String) {
        viewModelScope.launch {
            val res = addAlias(userId, from, to)
            when (res) {
                is AddAliasUseCase.Result.Success -> {
                    _state.update {
                        it.copy(message = "✅ Alias qo'shildi — ${res.movedCount} ta yozuv ko'chirildi")
                    }
                    refresh()
                }
                is AddAliasUseCase.Result.Failure -> {
                    _state.update { it.copy(message = "❌ ${res.reason}") }
                }
            }
        }
    }

    fun renameAction(oldName: String, newName: String) {
        viewModelScope.launch {
            val moved = rename(userId, oldName, newName)
            if (moved > 0) {
                _state.update { it.copy(message = "✅ Qayta nomlandi — $moved ta yozuv") }
            } else {
                _state.update { it.copy(message = "❌ Yozuv topilmadi yoki nomlar bir xil") }
            }
        }
    }

    fun delete(alias: String) {
        viewModelScope.launch {
            deleteAlias(userId, alias)
            _state.update { it.copy(message = "🗑 Alias o'chirildi") }
            refresh()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
