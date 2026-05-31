package uz.daftar.app.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.TransactionEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class SearchMode { CLIENT, DATE_RANGE, DATE_SINGLE }

/** Faol qidiruv parametrlari (performSearch bosilganda o'rnatiladi) */
data class SearchParams(
    val mode: SearchMode,
    val query: String,
    val from: LocalDate,
    val to: LocalDate
)

data class SearchState(
    val mode: SearchMode = SearchMode.CLIENT,
    val query: String = "",
    val dateFrom: LocalDate = LocalDate.now().minusMonths(1),
    val dateTo: LocalDate = LocalDate.now(),
    val searched: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val txDao: TransactionDao
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val params = MutableStateFlow<SearchParams?>(null)

    /** Paging 3 natijalar oqimi — parametr o'zgarsa qayta yuklanadi */
    val results: Flow<PagingData<TransactionEntity>> = params
        .flatMapLatest { p ->
            if (p == null) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(
                        pageSize = 30,
                        prefetchDistance = 15,
                        enablePlaceholders = false
                    )
                ) {
                    when (p.mode) {
                        SearchMode.CLIENT ->
                            txDao.pagingByClientLike(userId, p.query.trim())
                        SearchMode.DATE_RANGE ->
                            txDao.pagingByRange(userId, dayStart(p.from), dayEnd(p.to))
                        SearchMode.DATE_SINGLE ->
                            txDao.pagingByRange(userId, dayStart(p.from), dayEnd(p.from))
                    }
                }.flow
            }
        }
        .cachedIn(viewModelScope)

    fun setMode(mode: SearchMode) {
        _state.update { it.copy(mode = mode, searched = false) }
        params.value = null
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setDateFrom(d: LocalDate) = _state.update { it.copy(dateFrom = d) }
    fun setDateTo(d: LocalDate) = _state.update { it.copy(dateTo = d) }

    fun performSearch() {
        val s = _state.value
        if (s.mode == SearchMode.CLIENT && s.query.trim().isEmpty()) return
        _state.update { it.copy(searched = true) }
        params.value = SearchParams(s.mode, s.query, s.dateFrom, s.dateTo)
    }

    private fun dayStart(d: LocalDate): String = d.atStartOfDay().format(ISO)
    private fun dayEnd(d: LocalDate): String = d.plusDays(1).atStartOfDay().format(ISO)

    companion object {
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
