package uz.daftar.app.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.usecase.SearchTransactionsUseCase
import java.time.LocalDate
import javax.inject.Inject

enum class SearchMode { CLIENT, DATE_RANGE, DATE_SINGLE }

data class SearchState(
    val mode: SearchMode = SearchMode.CLIENT,
    val query: String = "",
    val dateFrom: LocalDate = LocalDate.now().minusMonths(1),
    val dateTo: LocalDate = LocalDate.now(),
    val results: List<TransactionEntity> = emptyList(),
    val isSearching: Boolean = false,
    val searched: Boolean = false  // Hech bo'lmaganda bir marta qidirilganmi
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchTransactionsUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    fun setMode(mode: SearchMode) {
        _state.update { it.copy(mode = mode, results = emptyList(), searched = false) }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
    }

    fun setDateFrom(d: LocalDate) {
        _state.update { it.copy(dateFrom = d) }
    }

    fun setDateTo(d: LocalDate) {
        _state.update { it.copy(dateTo = d) }
    }

    fun performSearch() {
        val s = state.value
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, searched = true) }
            val result = when (s.mode) {
                SearchMode.CLIENT -> search.byClientName(userId, s.query)
                SearchMode.DATE_RANGE -> search.byDateRange(userId, s.dateFrom, s.dateTo)
                SearchMode.DATE_SINGLE -> search.byDate(userId, s.dateFrom)
            }
            _state.update { it.copy(results = result, isSearching = false) }
        }
    }
}
