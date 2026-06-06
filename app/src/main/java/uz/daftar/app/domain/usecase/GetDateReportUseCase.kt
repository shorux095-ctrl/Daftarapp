package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.dao.YukNarxDao
import uz.daftar.app.data.db.dao.ClientPriceDao
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
    private val priceDao: PriceHistoryDao,
    private val clientPriceDao: ClientPriceDao
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
        // Har mijoz uchun N narx TARIXI: type -> sorted [(date, price)] (har yozuv sanasiga qarab tanlash uchun)
        val nHistByClient = mutableMapOf<String, Map<String, List<Pair<String, Double>>>>()
        val cargoTypes = listOf("a", "b", "c", "d", "k")
        for (cl in clientLowers) {
            val all = runCatching { priceDao.getAllForClient(userId, cl) }.getOrDefault(emptyList())
            nHistByClient[cl] = all.groupBy { it.priceType.lowercase() }
                .mapValues { e -> e.value.map { it.date to it.price }.sortedBy { it.first } }
        }

        // Mijozning JORIY narxi (client_prices) — price_history bo'sh bo'lsa zaxira
        val cPriceByClient = mutableMapOf<String, Map<String, Double?>>()
        for (cl in clientLowers) {
            val cp = runCatching { clientPriceDao.get(userId, cl) }.getOrNull()
            cPriceByClient[cl] = mapOf(
                "a" to cp?.aPrice, "b" to cp?.bPrice, "c" to cp?.cPrice,
                "d" to cp?.dPrice, "k" to cp?.kPrice
            )
        }

        // Global T narxlar (T va T1)
        val tPrices = mutableMapOf<String, Double?>()
        val t1Prices = mutableMapOf<String, Double?>()
        for (t in cargoTypes) {
            tPrices[t] = yukNarxDao.getLatestGlobal(userId, t, "t")?.price
            t1Prices[t] = yukNarxDao.getLatestGlobal(userId, t, "t1")?.price
        }

        // Sana bo'yicha narx: berilgan sana yoki undan oldingi eng oxirgi; topilmasa eng birinchi (retroaktiv)
        fun priceAtList(list: List<Pair<String, Double>>?, date: String): Double? {
            if (list.isNullOrEmpty()) return null
            val day = date.take(10)
            var best: Double? = null
            for ((d, p) in list) { if (d.take(10) <= day) best = p else break }
            return best ?: list.first().second
        }

        fun effectivePrice(tx: TransactionEntity): Double? {
            val t = tx.type.lowercase()
            return if (useNarx) {
                // N narx (sotilgan narx): O'SHA SANADAGI narx → joriy narx (client_prices) → global T
                priceAtList(nHistByClient[tx.clientName.lowercase()]?.get(t), tx.date)
                    ?: cPriceByClient[tx.clientName.lowercase()]?.get(t)
                    ?: tPrices[t]
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
