package uz.daftar.app.ui.screen.tahrir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.daftar.app.data.repository.TransactionRepository
import uz.daftar.app.domain.model.Transaction
import uz.daftar.app.domain.model.TxType
import java.time.LocalDate
import javax.inject.Inject

/** Bitta qator: yozuv + uning T tannarxi (ko'rsatish uchun) */
data class TahrirRowData(
    val tx: Transaction,
    val costPrice: Double?   // null = narx yo'q (yoki P/Q)
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TahrirViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val txDao: uz.daftar.app.data.db.dao.TransactionDao,
    private val yukDao: uz.daftar.app.data.db.dao.YukNarxDao,
    private val lockManager: uz.daftar.app.core.security.LockManager
) : ViewModel() {

    private val userId = 1L

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _nameFilter = MutableStateFlow("")
    val nameFilter: StateFlow<String> = _nameFilter.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Tanlangan sana + ism filtri bo'yicha o'sha kunning yozuvlari (T narx bilan) */
    val rows: StateFlow<List<TahrirRowData>> =
        combine(_date, _nameFilter) { d, nf -> Pair(d, nf) }
            .flatMapLatest { (d, nf) ->
                repo.observeBetween(userId, d, d.plusDays(1)).map { list ->
                    val f = nf.trim()
                    val filtered = (if (f.isEmpty()) list
                        else list.filter { it.clientName.contains(f, ignoreCase = true) })
                        .sortedBy { it.date }
                    // Global T / T1 narx tarixi (turlar bo'yicha, sanaga saralangan)
                    val tHist = yukDao.getAllGlobalGroup(userId, "t")
                        .groupBy { it.type.lowercase() }
                        .mapValues { (_, v) -> v.map { it.date to it.price }.sortedBy { it.first } }
                    val t1Hist = yukDao.getAllGlobalGroup(userId, "t1")
                        .groupBy { it.type.lowercase() }
                        .mapValues { (_, v) -> v.map { it.date to it.price }.sortedBy { it.first } }
                    filtered.map { tx -> TahrirRowData(tx, costOf(tx, tHist, t1Hist)) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Yozuvning amaldagi T tannarxi: override → (T1 bo'lsa T1) → global T (o'sha sanada) */
    private fun costOf(
        tx: Transaction,
        tHist: Map<String, List<Pair<String, Double>>>,
        t1Hist: Map<String, List<Pair<String, Double>>>
    ): Double? {
        if (tx.type == TxType.P || tx.type == TxType.Q) return null
        tx.tOverride?.let { return it }
        val code = tx.type.code.lowercase()
        val day = tx.date.toLocalDate().toString()  // yyyy-MM-dd
        return if (tx.costTier == "t1")
            (globalAt(t1Hist[code], day) ?: globalAt(tHist[code], day))
        else globalAt(tHist[code], day)
    }

    /** Berilgan kun yoki undan oldingi eng oxirgi global narx */
    private fun globalAt(list: List<Pair<String, Double>>?, day: String): Double? {
        if (list.isNullOrEmpty()) return null
        var best: Double? = null
        for ((d, p) in list) { if (d.take(10) <= day) best = p else break }
        return best ?: list.first().second
    }

    fun setDate(d: LocalDate) { _date.value = d }
    fun prevDay() { _date.value = _date.value.minusDays(1) }
    fun nextDay() { _date.value = _date.value.plusDays(1) }
    fun today() { _date.value = LocalDate.now() }
    fun setNameFilter(s: String) { _nameFilter.value = s }
    fun clearMessage() { _message.value = null }

    /** Bitta yozuvni T yoki T1 tarifga qo'yish (har birini alohida) */
    fun setTier(tx: Transaction, tier: String) {
        if (tx.type == TxType.P || tx.type == TxType.Q) return
        val t = if (tier == "t1") "t1" else "t"
        if ((tx.costTier ?: "t") == t) return
        viewModelScope.launch {
            txDao.setTierById(tx.id, t)
            _message.value = if (t == "t1") "→ T1 tarif" else "→ T tarif"
        }
    }

    /** Bitta yozuvni o'chirish */
    fun delete(id: Long) {
        viewModelScope.launch {
            repo.deleteByIds(listOf(id))
            _message.value = "🗑 O'chirildi"
        }
    }

    /** PIN o'rnatilganmi (Sozlamalardagi ilova PINi) */
    val pinSet: StateFlow<Boolean> =
        lockManager.pinCode.map { it != null }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Ekranda ko'rinayotgan barcha yozuvlarni o'chirish.
     *  PIN o'rnatilgan bo'lsa — kiritilgan kod mos kelmasa O'CHIRMAYDI. */
    fun deleteAllShown(pinInput: String? = null, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val pin = lockManager.pinCode.first()
            if (pin != null && pin != pinInput?.trim()) {
                _message.value = "❌ Kod noto'g'ri"
                return@launch
            }
            val ids = rows.value.map { it.tx.id }
            if (ids.isEmpty()) { _message.value = "O'chirish uchun yozuv yo'q"; return@launch }
            repo.deleteByIds(ids)
            _message.value = "🗑 ${ids.size} ta yozuv o'chirildi"
            onDone()
        }
    }
}
