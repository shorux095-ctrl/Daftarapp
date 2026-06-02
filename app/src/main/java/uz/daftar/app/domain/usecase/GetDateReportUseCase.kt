package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.dao.YukNarxDao
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import java.time.LocalDate
import javax.inject.Inject

/** Bot uslubы "📅 dd.MM" hisoboti ma'lumotlari */
data class DateReport(
    val date: LocalDate,
    val title: String,                          // "30.05" yoki "26.05–01.06"
    val clientLines: List<DateReportClientLine>,
    val totalsByType: Map<TxType, Double>,
    val revenueByType: Map<TxType, Double>,
    val totalRevenue: Double,
    val totalPayments: Double,
    val useNarx: Boolean = false
)

data class DateReportClientLine(
    val clientName: String,
    val entries: List<DateReportEntry>
)

data class DateReportEntry(
    val type: TxType,
    val amount: Double,
    /** Yuk turlari uchun: ta'sirli narx (tOverride → N narx → global T). P/Q uchun null. */
    val price: Double?
)

class GetDateReportUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val yukNarxDao: YukNarxDao,
    private val priceDao: PriceHistoryDao
) {
    suspend operator fun invoke(userId: Long, date: LocalDate, types: Set<String>? = null, useNarx: Boolean = false): DateReport {
        val title = date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))
        return build(userId, date, date.plusDays(1), title, types, useNarx)
    }

    /** Sana oralig'i uchun (masalan haftalik: dushanba–yakshanba) */
    suspend fun range(
        userId: Long,
        startInclusive: LocalDate,
        endInclusive: LocalDate,
        types: Set<String>? = null,
        useNarx: Boolean = false
    ): DateReport {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM")
        val title = "${startInclusive.format(fmt)}–${endInclusive.format(fmt)}"
        return build(userId, startInclusive, endInclusive.plusDays(1), title, types, useNarx)
    }

    private suspend fun build(
        userId: Long,
        startInclusive: LocalDate,
        endExclusive: LocalDate,
        title: String,
        types: Set<String>?,
        useNarx: Boolean
    ): DateReport {
        val dayStart = "$startInclusive 00:00:00"
        val dayEnd = "$endExclusive 00:00:00"
        val allTxs = txDao.getRange(userId, dayStart, dayEnd).sortedBy { it.date }
        val txs = if (types.isNullOrEmpty()) allTxs
                  else allTxs.filter { it.type.lowercase() in types }

        // Har mijoz uchun N narxlarini olish (sana boshi bilan)
        val clientLowers = txs.map { it.clientName.lowercase() }.distinct()
        val nPricesByClient = mutableMapOf<String, MutableMap<String, Double?>>()
        val cargoTypes = listOf("a", "b", "c", "d", "k")
        for (cl in clientLowers) {
            val m = mutableMapOf<String, Double?>()
            for (t in cargoTypes) {
                m[t] = priceDao.getPriceAt(userId, cl, t, dayEnd)
            }
            nPricesByClient[cl] = m
        }

        // Global T narxlar (T va T1)
        val tPrices = mutableMapOf<String, Double?>()
        val t1Prices = mutableMapOf<String, Double?>()
        for (t in cargoTypes) {
            tPrices[t] = yukNarxDao.getLatestGlobal(userId, t, "t")?.price
            t1Prices[t] = yukNarxDao.getLatestGlobal(userId, t, "t1")?.price
        }

        fun effectivePrice(tx: TransactionEntity): Double? {
            val t = tx.type.lowercase()
            return if (useNarx) {
                // N narx (sotilgan narx): mijoz N narxи → global T
                nPricesByClient[tx.clientName.lowercase()]?.get(t) ?: tPrices[t]
            } else {
                // T narx (J): bir martalik T override → T1 tarif → global T
                tx.tOverride ?: if (tx.costTier == "t1") (t1Prices[t] ?: tPrices[t]) else tPrices[t]
            }
        }

        // Mijozlarni paydo bo'lish tartibida guruhlash
        val clientOrder = mutableListOf<String>()
        val clientTxs = mutableMapOf<String, MutableList<TransactionEntity>>()
        for (tx in txs) {
            val cl = tx.clientName
            if (cl !in clientTxs) {
                clientOrder.add(cl)
                clientTxs[cl] = mutableListOf()
            }
            clientTxs[cl]!!.add(tx)
        }

        val clientLines = mutableListOf<DateReportClientLine>()
        for (cl in clientOrder) {
            val entries = clientTxs[cl]!!.map { tx ->
                val type = TxType.fromCode(tx.type) ?: TxType.A
                val price = if (type in setOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)) {
                    effectivePrice(tx)
                } else null
                DateReportEntry(type = type, amount = tx.amount, price = price)
            }
            clientLines.add(DateReportClientLine(clientName = cl, entries = entries))
        }

        // JAMI hisoblash
        val totalsByType = mutableMapOf<TxType, Double>()
        val revenueByType = mutableMapOf<TxType, Double>()
        var totalRevenue = 0.0
        var totalPayments = 0.0
        for (tx in txs) {
            val type = TxType.fromCode(tx.type) ?: continue
            when (type) {
                TxType.P -> totalPayments += tx.amount
                TxType.Q -> Unit // Q daromad emas
                else -> {
                    totalsByType[type] = (totalsByType[type] ?: 0.0) + tx.amount
                    val price = effectivePrice(tx)
                    if (price != null) {
                        val rev = tx.amount * price
                        revenueByType[type] = (revenueByType[type] ?: 0.0) + rev
                        totalRevenue += rev
                    }
                }
            }
        }

        return DateReport(
            date = startInclusive,
            title = title,
            clientLines = clientLines,
            totalsByType = totalsByType,
            revenueByType = revenueByType,
            totalRevenue = totalRevenue,
            totalPayments = totalPayments,
            useNarx = useNarx
        )
    }
}
