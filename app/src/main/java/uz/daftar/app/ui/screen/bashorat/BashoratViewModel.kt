package uz.daftar.app.ui.screen.bashorat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.dao.TransactionDao
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.roundToInt

enum class PredStatus { OVERDUE, TODAY, TOMORROW, SOON, LATER }

enum class PredFilter { ALL, OVERDUE, TODAY, TOMORROW, WEEK }

data class ClientPrediction(
    val clientName: String,
    val lastDate: LocalDate,
    val daysSinceLast: Long,
    val avgInterval: Int,        // odatda har necha kunda (mediana)
    val nextExpected: LocalDate, // keyingi taxminiy sana
    val daysUntilNext: Long,     // bugundan; manfiy = kechikkan
    val eventCount: Int,         // necha marta yuk olgan
    val reliable: Boolean,       // ishonchli (>=4 marta + barqaror oraliq)
    val status: PredStatus
)

data class BashoratState(
    val isLoading: Boolean = true,
    val all: List<ClientPrediction> = emptyList(),
    val query: String = "",
    val filter: PredFilter = PredFilter.ALL,
    val countToday: Int = 0,
    val countTomorrow: Int = 0,
    val countOverdue: Int = 0,
    val countWeek: Int = 0,
    val error: String? = null
)

@HiltViewModel
class BashoratViewModel @Inject constructor(
    private val txDao: TransactionDao
) : ViewModel() {

    private val userId = 1L
    private val cargo = setOf("a", "b", "c", "d", "k")
    private val isoDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _state = MutableStateFlow(BashoratState())
    val state: StateFlow<BashoratState> = _state.asStateFlow()

    init { load() }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setFilter(f: PredFilter) = _state.update { it.copy(filter = f) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val today = LocalDate.now()
                // Barcha tarix (juda erta sanadan ertagacha) — bitta so'rov
                val txs = txDao.getRange(userId, "2000-01-01", today.plusDays(1).format(isoDate))

                // Mijoz -> yuk olgan KUNLAR (faqat cargo turlari, kun bo'yicha noyob)
                val byClient = HashMap<String, MutableSet<LocalDate>>()
                val display = HashMap<String, String>()
                for (tx in txs) {
                    val code = tx.type.lowercase()
                    if (code !in cargo) continue
                    val key = tx.clientName.trim().lowercase()
                    if (key.isEmpty()) continue
                    display.putIfAbsent(key, tx.clientName.trim())
                    val d = parseDay(tx.date) ?: continue
                    byClient.getOrPut(key) { mutableSetOf() }.add(d)
                }

                val preds = byClient.mapNotNull { (key, daysSet) ->
                    val days = daysSet.sorted()
                    if (days.size < 3) return@mapNotNull null  // kamida 3 marta — aks holda bashorat ishonarsiz

                    // Qo'shni kunlar orasidagi oraliqlar (kun)
                    val gaps = ArrayList<Long>()
                    for (i in 1 until days.size) {
                        val g = ChronoUnit.DAYS.between(days[i - 1], days[i])
                        if (g > 0) gaps.add(g)
                    }
                    if (gaps.size < 2) return@mapNotNull null

                    val last = days.last()
                    val recent = gaps.takeLast(6)               // yaqin xulqqa e'tibor
                    val interval = median(recent).coerceAtLeast(1)
                    val next = last.plusDays(interval.toLong())
                    val daysSince = ChronoUnit.DAYS.between(last, today)
                    val until = ChronoUnit.DAYS.between(today, next)
                    val reliable = days.size >= 4 && consistent(recent)

                    val status = when {
                        until < 0L -> PredStatus.OVERDUE
                        until == 0L -> PredStatus.TODAY
                        until == 1L -> PredStatus.TOMORROW
                        until <= 7L -> PredStatus.SOON
                        else -> PredStatus.LATER
                    }

                    ClientPrediction(
                        clientName = display[key] ?: key,
                        lastDate = last,
                        daysSinceLast = daysSince,
                        avgInterval = interval,
                        nextExpected = next,
                        daysUntilNext = until,
                        eventCount = days.size,
                        reliable = reliable,
                        status = status
                    )
                }.sortedWith(compareBy({ statusOrder(it.status) }, { it.daysUntilNext }))  // Bugun → Ertaga → yaqin → Kechikkan

                _state.update {
                    it.copy(
                        isLoading = false,
                        all = preds,
                        error = null,
                        countToday = preds.count { p -> p.status == PredStatus.TODAY },
                        countTomorrow = preds.count { p -> p.status == PredStatus.TOMORROW },
                        countOverdue = preds.count { p -> p.status == PredStatus.OVERDUE },
                        countWeek = preds.count { p ->
                            p.status == PredStatus.TODAY || p.status == PredStatus.TOMORROW || p.status == PredStatus.SOON
                        }
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun statusOrder(s: PredStatus): Int = when (s) {
        PredStatus.TODAY -> 0
        PredStatus.TOMORROW -> 1
        PredStatus.SOON -> 2
        PredStatus.LATER -> 3
        PredStatus.OVERDUE -> 4
    }

    private fun parseDay(date: String): LocalDate? = try {
        LocalDate.parse(date.take(10), isoDate)
    } catch (_: Exception) {
        null
    }

    private fun median(xs: List<Long>): Int {
        if (xs.isEmpty()) return 0
        val s = xs.sorted()
        val mid = s.size / 2
        val m = if (s.size % 2 == 1) s[mid].toDouble() else (s[mid - 1] + s[mid]) / 2.0
        return m.roundToInt()
    }

    // Oraliqlar barqarormi? (eng katta oraliq eng kichikdan ~2.5 baravardan oshmasa)
    private fun consistent(xs: List<Long>): Boolean {
        val s = xs.filter { it > 0 }
        if (s.size < 2) return false
        val mn = s.minOrNull() ?: return false
        val mx = s.maxOrNull() ?: return false
        return mx <= mn * 2.5 + 1
    }
}
