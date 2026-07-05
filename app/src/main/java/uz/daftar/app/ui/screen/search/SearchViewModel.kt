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
    private val txDao: TransactionDao,
    private val priceDao: uz.daftar.app.data.db.dao.PriceHistoryDao
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    // ✨ Ism yozishda YORDAM (autocomplete) uchun barcha mijoz nomlari
    private val _allNames = MutableStateFlow<List<String>>(emptyList())
    val allNames: StateFlow<List<String>> = _allNames.asStateFlow()

    // 💰 Har yozuvdan KEYINGI qoldiq qarz (tx.id -> qoldi) — bitta mijoz qidirilganda
    private val _balances = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val balances: StateFlow<Map<Long, Long>> = _balances.asStateFlow()

    init {
        viewModelScope.launch {
            _allNames.value = runCatching { txDao.getAllClientNames(userId) }.getOrDefault(emptyList())
        }
    }

    /** Bitta mijoz aniqlansa — har yozuvdan keyingi qoldiqni hisoblaydi (tarixdagi kabi). */
    private suspend fun computeBalances(name: String) {
        val txs = runCatching { txDao.getByClient(userId, name.lowercase()) }.getOrDefault(emptyList())
            .sortedBy { it.date }
        if (txs.isEmpty()) { _balances.value = emptyMap(); return }
        val pricesByType = runCatching { priceDao.getAllForClient(userId, name.lowercase()) }
            .getOrDefault(emptyList())
            .groupBy { it.priceType }
            .mapValues { (_, l) -> l.sortedBy { it.date } }
        fun priceAt(type: String, at: String): Double? {
            val prices = pricesByType[type] ?: return null
            var best: Double? = null
            for (p in prices) { if (p.date.take(10) <= at.take(10)) best = p.price else break }
            return best ?: prices.firstOrNull()?.price
        }
        var running = 0.0
        val map = mutableMapOf<Long, Long>()
        for (tx in txs) {
            when (tx.type.lowercase()) {
                "p" -> running -= tx.amount
                "q" -> running += tx.amount
                else -> { val p = priceAt(tx.type, tx.date); if (p != null) running += tx.amount * p }
            }
            map[tx.id] = Math.round(running)
        }
        _balances.value = map
    }

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
        // 💰 Bitta mijoz topilsa — qoldiq qarzlarni ham hisoblaymiz
        if (s.mode == SearchMode.CLIENT) {
            viewModelScope.launch {
                val q = s.query.trim().lowercase()
                val matches = _allNames.value.filter { it.contains(q) }
                val name = when {
                    _allNames.value.contains(q) -> q
                    matches.size == 1 -> matches[0]
                    else -> null
                }
                if (name != null) computeBalances(name) else _balances.value = emptyMap()
            }
        } else _balances.value = emptyMap()
    }

    private fun dayStart(d: LocalDate): String = d.atStartOfDay().format(ISO)
    private fun dayEnd(d: LocalDate): String = d.plusDays(1).atStartOfDay().format(ISO)

    companion object {
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
