package uz.daftar.app.ui.screen.sklad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.dao.SkladDao
import uz.daftar.app.data.db.entity.SkladEntity
import uz.daftar.app.data.repository.TransactionRepository
import javax.inject.Inject

/** Bitta tovar bo'yicha yig'ma */
data class SkladItemSum(
    val name: String,
    val keldi: Double,    // kirim jami
    val chiqdi: Double,   // chiqim jami
    val qoldi: Double,    // keldi - chiqdi
    val pulKirim: Double, // kirim summasi (qty*price)
    val pulChiqim: Double, // chiqim summasi
    val oxirgiKelgan: Long, // oxirgi kirim sanasi (ms)
    val oxirgiNarx: Double  // oxirgi kirim narxi
)

/** Yuk turi bo'yicha qoldiq (A/B/C/D/K) — kirim manual, sotilgan tranzaksiyalardan */
data class SkladTypeStock(
    val type: String,   // A/B/C/D/K
    val kelgan: Double,  // skladga qo'lда kiritilgan kirim
    val sotilgan: Double, // mijozlarga sotilgan (tranzaksiyalardan)
    val qolgan: Double
)

@HiltViewModel
class SkladViewModel @Inject constructor(
    private val dao: SkladDao,
    private val txRepo: TransactionRepository
) : ViewModel() {

    private val userId = 1L
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val items: StateFlow<List<SkladEntity>> =
        dao.all(userId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Yuk turlari bo'yicha qoldiq — Sklad ochilganda hisoblanadi
    private val _typeStock = MutableStateFlow<List<SkladTypeStock>>(emptyList())
    val typeStock: StateFlow<List<SkladTypeStock>> = _typeStock.asStateFlow()

    init { refreshTypeStock() }

    /** Yuk turlari bo'yicha: kirim (sklad) − sotilgan (tranzaksiya) = qoldiq */
    fun refreshTypeStock() {
        viewModelScope.launch {
            val txs = runCatching { txRepo.getAllForUser(userId) }.getOrDefault(emptyList())
            // Mijozlarga sotilgan yuk (faqat A/B/C/D/K turlari)
            val soldByType = txs.filter { it.type.uppercase() in listOf("A", "B", "C", "D", "K") }
                .groupBy { it.type.uppercase() }
                .mapValues { (_, rows) -> rows.sumOf { it.amount } }
            // Skladga qo'lда kiritilgan kirim — tovar nomi turga teng bo'lsa (a/b/c/d/k)
            val skladRows = items.value
            val inByType = skladRows.filter { it.isIn && it.name.trim().uppercase() in listOf("A", "B", "C", "D", "K") }
                .groupBy { it.name.trim().uppercase() }
                .mapValues { (_, rows) -> rows.sumOf { it.qty } }
            val allTypes = (soldByType.keys + inByType.keys).toSortedSet()
            _typeStock.value = allTypes.map { t ->
                val k = inByType[t] ?: 0.0
                val s = soldByType[t] ?: 0.0
                SkladTypeStock(type = t, kelgan = k, sotilgan = s, qolgan = k - s)
            }
        }
    }

    fun add(name: String, qtyStr: String, priceStr: String, isIn: Boolean) {
        val n = name.trim()
        val qty = qtyStr.trim().replace(",", ".").toDoubleOrNull()
        val price = priceStr.trim().replace(",", ".").toDoubleOrNull() ?: 0.0
        if (n.isEmpty()) { _message.value = "❌ Tovar nomini yozing"; return }
        if (qty == null || qty <= 0) { _message.value = "❌ Miqdorni to'g'ri yozing"; return }
        viewModelScope.launch {
            dao.insert(SkladEntity(userId = userId, name = n, qty = qty, price = price, isIn = isIn))
            _message.value = if (isIn) "✅ Kirim qo'shildi" else "✅ Chiqim qo'shildi"
            refreshTypeStock()
        }
    }

    fun delete(item: SkladEntity) {
        viewModelScope.launch {
            dao.delete(item.id)
            _message.value = "🗑 O'chirildi"
        }
    }

    fun clearMessage() { _message.value = null }

    companion object {
        /** Yozuvlardan tovar bo'yicha yig'mani hisoblaydi */
        fun summarize(list: List<SkladEntity>): List<SkladItemSum> {
            return list.groupBy { it.name.trim().lowercase() }.map { (_, rows) ->
                val keldi = rows.filter { it.isIn }.sumOf { it.qty }
                val chiqdi = rows.filter { !it.isIn }.sumOf { it.qty }
                val pulKirim = rows.filter { it.isIn }.sumOf { it.qty * it.price }
                val pulChiqim = rows.filter { !it.isIn }.sumOf { it.qty * it.price }
                val oxirgiKirim = rows.filter { it.isIn }.maxByOrNull { it.date }
                SkladItemSum(
                    name = rows.first().name,
                    keldi = keldi,
                    chiqdi = chiqdi,
                    qoldi = keldi - chiqdi,
                    pulKirim = pulKirim,
                    pulChiqim = pulChiqim,
                    oxirgiKelgan = oxirgiKirim?.date ?: 0L,
                    oxirgiNarx = oxirgiKirim?.price ?: 0.0
                )
            }.sortedBy { it.name.lowercase() }
        }
    }
}
