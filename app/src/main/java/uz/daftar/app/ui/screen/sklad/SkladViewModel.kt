package uz.daftar.app.ui.screen.sklad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.dao.SkladDao
import uz.daftar.app.data.db.entity.SkladEntity
import uz.daftar.app.data.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    val sotilgan: Double, // mijozlarga sotilgan (qo'shilgan kundan beri)
    val qolgan: Double,
    val baselineLabel: String? = null // qaysi kundan beri sotilgani hisoblanadi (dd.MM.yyyy)
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

    // Yuk turlari bo'yicha qoldiq — sklad kirimi + mijozga sotilgan (ikkalasi ham reaktiv Flow).
    // MUHIM: sotilganlar FAQAT shu turni skladga BIRINCHI qo'shilgan kundan beri hisoblanadi.
    // Ya'ni eski (qo'shishdan oldingi) sotuvlar AYIRILMAYDI — shunda qoldiq to'g'ri chiqadi.
    val typeStock: StateFlow<List<SkladTypeStock>> =
        combine(
            dao.all(userId),
            txRepo.observeCargoSales(userId)
        ) { skladRows, cargoSales ->
            val zone = ZoneId.of("Asia/Tashkent")
            val inByType = skladRows
                .filter { it.isIn && it.name.trim().uppercase() in CARGO_TYPES }
                .groupBy { it.name.trim().uppercase() }
            CARGO_TYPES.map { t ->
                val rows = inByType[t]
                if (rows == null) {
                    // Hali skladga kiritilmagan tur — hisob yo'q, hech narsa ayirilmaydi
                    SkladTypeStock(type = t, kelgan = 0.0, sotilgan = 0.0, qolgan = 0.0, baselineLabel = null)
                } else {
                    val kelgan = rows.sumOf { it.qty }
                    // Shu turni skladga BIRINCHI qo'shilgan kun → shu kundan beri sotilganlar ayriladi
                    val baselineDate = Instant.ofEpochMilli(rows.minOf { it.date })
                        .atZone(zone).toLocalDate()
                    val baselineKey = baselineDate.toString() // "yyyy-MM-dd"
                    val sotilgan = cargoSales
                        .filter { it.type.equals(t, ignoreCase = true) && txDay(it.date) >= baselineKey }
                        .sumOf { it.amount }
                    SkladTypeStock(
                        type = t,
                        kelgan = kelgan,
                        sotilgan = sotilgan,
                        qolgan = kelgan - sotilgan,
                        baselineLabel = fmtDay(baselineDate)
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, qtyStr: String, priceStr: String, isIn: Boolean) {
        val n = name.trim()
        val qty = qtyStr.trim().replace(",", ".").toDoubleOrNull()
        val price = priceStr.trim().replace(",", ".").toDoubleOrNull() ?: 0.0
        if (n.isEmpty()) { _message.value = "❌ Tovar nomini yozing"; return }
        if (qty == null || qty <= 0) { _message.value = "❌ Miqdorni to'g'ri yozing"; return }
        viewModelScope.launch {
            dao.insert(SkladEntity(userId = userId, name = n, qty = qty, price = price, isIn = isIn))
            _message.value = if (isIn) "✅ Kirim qo'shildi" else "✅ Chiqim qo'shildi"
        }
    }

    fun delete(item: SkladEntity) {
        viewModelScope.launch {
            dao.delete(item.id)
            _message.value = "🗑 O'chirildi"
        }
    }

    /** Mavjud yozuvni tahrirlash (nom/miqdor/narx/kirim-chiqim/sana). */
    fun update(id: Long, name: String, qtyStr: String, priceStr: String, isIn: Boolean, dateMs: Long) {
        val n = name.trim()
        val qty = qtyStr.trim().replace(",", ".").toDoubleOrNull()
        val price = priceStr.trim().replace(",", ".").toDoubleOrNull() ?: 0.0
        if (n.isEmpty()) { _message.value = "❌ Tovar nomini yozing"; return }
        if (qty == null || qty <= 0) { _message.value = "❌ Miqdorni to'g'ri yozing"; return }
        viewModelScope.launch {
            dao.update(id, n, qty, price, isIn, dateMs)
            _message.value = "✏️ Tahrirlandi"
        }
    }

    fun clearMessage() { _message.value = null }

    companion object {
        /** Yuk turlari — A/B/C/D/K (qat'iy ro'yxat, tartibda) */
        val CARGO_TYPES = listOf("A", "B", "C", "D", "K")

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

/** Tranzaksiya sanasidan faqat kun qismi ("yyyy-MM-dd") — 'T'/probel formatiga ham mos. */
private fun txDay(d: String): String = d.replace('T', ' ').take(10)

/** LocalDate → "dd.MM.yyyy" (ko'rsatish uchun). */
private fun fmtDay(d: LocalDate): String =
    "%02d.%02d.%04d".format(d.dayOfMonth, d.monthValue, d.year)
