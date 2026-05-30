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
    val clientLines: List<DateReportClientLine>,
    val totalsByType: Map<TxType, Double>,    // Har yuk turi bo'yicha jami miqdor (A 150, C 2.902, ...)
    val revenueByType: Map<TxType, Double>,    // Har yuk turi bo'yicha jami daromad (miqdor × narx)
    val totalRevenue: Double,                   // J — barcha yuk turlari yig'indisi
    val totalPayments: Double                   // 🅿️ — barcha P (to'lov)lar
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
    suspend operator fun invoke(userId: Long, date: LocalDate): DateReport {
        val dayStart = "$date 00:00:00"
        val dayEnd = date.plusDays(1).toString() + " 00:00:00"
        val txs = txDao.getRange(userId, dayStart, dayEnd).sortedBy { it.date }

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

        // Global T narxlar
        val tPrices = mutableMapOf<String, Double?>()
        for (t in cargoTypes) {
            tPrices[t] = yukNarxDao.getLatestGlobal(userId, t, "t")?.price
        }

        fun effectivePrice(tx: TransactionEntity): Double? {
            tx.tOverride?.let { return it }
            val t = tx.type.lowercase()
            nPricesByClient[tx.clientName.lowercase()]?.get(t)?.let { return it }
            return tPrices[t]
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
            date = date,
            clientLines = clientLines,
            totalsByType = totalsByType,
            revenueByType = revenueByType,
            totalRevenue = totalRevenue,
            totalPayments = totalPayments
        )
    }
}
