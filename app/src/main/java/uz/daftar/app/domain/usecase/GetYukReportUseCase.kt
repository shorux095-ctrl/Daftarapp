package uz.daftar.app.domain.usecase

import kotlin.math.roundToLong
import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.dao.YukNarxDao
import uz.daftar.app.data.db.entity.TransactionEntity
import java.time.YearMonth
import javax.inject.Inject

/** Bitta qator: sana/oy + T, N, P, Farq (=P-N) */
data class YukReportRow(
    val label: String,
    val tTotal: Long,
    val nTotal: Long,
    val pTotal: Long,
    val farq: Long
)

data class YukReport(
    val title: String,
    val rows: List<YukReportRow>,
    val jamiT: Long,
    val jamiN: Long,
    val jamiP: Long,
    val jamiFarq: Long
)

/** Yuk soni hisoboti (pulsiz) — A B C D K miqdorlari */
data class YukCountRow(
    val label: String,
    val counts: Map<String, Double>
)

data class YukCountReport(
    val title: String,
    val rows: List<YukCountRow>,
    val totals: Map<String, Double>
)

/**
 * "📦 Yuk" hisoboti:
 *   T = yuk × global T narx
 *   N = yuk × mijoz N narxi (yo'q bo'lsa T)
 *   P = to'lovlar
 *   Farq = P − N
 */
class GetYukReportUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val yukNarxDao: YukNarxDao,
    private val priceDao: PriceHistoryDao
) {
    private val cargoTypes = setOf("a", "b", "c", "d", "k")

    private val monthsUz = listOf(
        "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
        "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr"
    )
    private val monthsUzShort = listOf(
        "Yan", "Fev", "Mar", "Apr", "May", "Iyn",
        "Iyl", "Avg", "Sen", "Okt", "Noy", "Dek"
    )

    suspend fun monthly(userId: Long, ym: YearMonth): YukReport {
        val start = ym.atDay(1).toString() + " 00:00:00"
        val end = ym.plusMonths(1).atDay(1).toString() + " 00:00:00"
        val txs = txDao.getRange(userId, start, end)
        val ctx = buildPriceContext(userId, txs)

        val rows = mutableListOf<YukReportRow>()
        var jt = 0L; var jn = 0L; var jp = 0L
        for (day in 1..ym.lengthOfMonth()) {
            val dayStr = ym.atDay(day).toString() // yyyy-MM-dd
            val dayTxs = txs.filter { it.date.startsWith(dayStr) }
            if (dayTxs.isEmpty()) continue
            val (t, n, p) = computeTNP(dayTxs, ctx)
            rows.add(YukReportRow("%02d.%02d".format(day, ym.monthValue), t, n, p, p - n))
            jt += t; jn += n; jp += p
        }
        return YukReport(
            title = "${monthsUz[ym.monthValue - 1]} ${ym.year}",
            rows = rows, jamiT = jt, jamiN = jn, jamiP = jp, jamiFarq = jp - jn
        )
    }

    suspend fun yearly(userId: Long, year: Int): YukReport {
        val start = "$year-01-01 00:00:00"
        val end = "${year + 1}-01-01 00:00:00"
        val txs = txDao.getRange(userId, start, end)
        val ctx = buildPriceContext(userId, txs)

        val rows = mutableListOf<YukReportRow>()
        var jt = 0L; var jn = 0L; var jp = 0L
        for (month in 1..12) {
            val mStr = "%04d-%02d".format(year, month)
            val mTxs = txs.filter { it.date.startsWith(mStr) }
            if (mTxs.isEmpty()) continue
            val (t, n, p) = computeTNP(mTxs, ctx)
            rows.add(YukReportRow(monthsUzShort[month - 1], t, n, p, p - n))
            jt += t; jn += n; jp += p
        }
        return YukReport(
            title = "$year-yil",
            rows = rows, jamiT = jt, jamiN = jn, jamiP = jp, jamiFarq = jp - jn
        )
    }

    // type -> [(date, price)] (sanaga ko'ra saralanган)
    private class PriceCtx(
        val globalT: Map<String, List<Pair<String, Double>>>,
        val clientN: Map<String, Map<String, List<Pair<String, Double>>>>
    )

    private suspend fun buildPriceContext(userId: Long, txs: List<TransactionEntity>): PriceCtx {
        val allGlobal = yukNarxDao.getAllGlobal(userId)
        val globalT = allGlobal
            .groupBy { it.type }
            .mapValues { (_, l) -> l.sortedBy { it.date }.map { it.date to it.price } }

        val clients = txs.map { it.clientName.lowercase() }.distinct()
        val clientN = mutableMapOf<String, Map<String, List<Pair<String, Double>>>>()
        for (c in clients) {
            val prices = priceDao.getAllForClient(userId, c)
            clientN[c] = prices
                .groupBy { it.priceType }
                .mapValues { (_, l) -> l.sortedBy { it.date }.map { it.date to it.price } }
        }
        return PriceCtx(globalT, clientN)
    }

    private fun priceAt(list: List<Pair<String, Double>>?, date: String): Double? {
        if (list.isNullOrEmpty()) return null
        var best: Double? = null
        // v154: KUN darajasida solishtirish (take(10)) — boshqa hisobotlar bilan bir xil.
        // Aks holda bugun 09:00 da qo'yilgan narx, bugun 08:00 dagi yukka tegmasdi.
        val day = date.take(10)
        for ((d, p) in list) {
            if (d.take(10) <= day) best = p else break
        }
        return best ?: list.first().second // retroaktiv
    }

    private fun computeTNP(txs: List<TransactionEntity>, ctx: PriceCtx): Triple<Long, Long, Long> {
        var t = 0.0; var n = 0.0; var p = 0.0
        for (tx in txs) {
            val type = tx.type.lowercase()
            when {
                type == "p" -> p += tx.amount
                type == "q" -> Unit
                type in cargoTypes -> {
                    // v154: T (tannarx) — o'z narxi; N (sotuv) — mijoz narxi.
                    // N topilmasa 0 (tannarxga TUSHMAYDI) — aks holda "Farq (P−N)" xato bo'lardi.
                    val tPrice = tx.tOverride ?: priceAt(ctx.globalT[type], tx.date)
                    val nPrice = priceAt(ctx.clientN[tx.clientName.lowercase()]?.get(type), tx.date)
                    if (tPrice != null) t += tx.amount * tPrice
                    if (nPrice != null) n += tx.amount * nPrice
                }
            }
        }
        // v154: roundToLong() — boshqa hisobotlar bilan bir xil (toLong() kesib tashlardi: 4999.9 → 4999)
        return Triple(t.roundToLong(), n.roundToLong(), p.roundToLong())
    }

    // ───────── YUK SONI (pulsiz, faqat miqdor) ─────────

    suspend fun countsMonthly(userId: Long, ym: YearMonth): YukCountReport {
        val start = ym.atDay(1).toString() + " 00:00:00"
        val end = ym.plusMonths(1).atDay(1).toString() + " 00:00:00"
        val txs = txDao.getRange(userId, start, end)
        val rows = mutableListOf<YukCountRow>()
        val totals = mutableMapOf<String, Double>()
        for (day in 1..ym.lengthOfMonth()) {
            val dayStr = ym.atDay(day).toString()
            val dayTxs = txs.filter { it.date.startsWith(dayStr) }
            if (dayTxs.isEmpty()) continue
            val counts = countByType(dayTxs)
            if (counts.values.all { it == 0.0 }) continue
            rows.add(YukCountRow("%02d.%02d".format(day, ym.monthValue), counts))
            for ((k, v) in counts) totals[k] = (totals[k] ?: 0.0) + v
        }
        return YukCountReport("${monthsUz[ym.monthValue - 1]} ${ym.year}", rows, totals)
    }

    suspend fun countsYearly(userId: Long, year: Int): YukCountReport {
        val start = "$year-01-01 00:00:00"
        val end = "${year + 1}-01-01 00:00:00"
        val txs = txDao.getRange(userId, start, end)
        val rows = mutableListOf<YukCountRow>()
        val totals = mutableMapOf<String, Double>()
        for (month in 1..12) {
            val mStr = "%04d-%02d".format(year, month)
            val mTxs = txs.filter { it.date.startsWith(mStr) }
            if (mTxs.isEmpty()) continue
            val counts = countByType(mTxs)
            if (counts.values.all { it == 0.0 }) continue
            rows.add(YukCountRow(monthsUzShort[month - 1], counts))
            for ((k, v) in counts) totals[k] = (totals[k] ?: 0.0) + v
        }
        return YukCountReport("$year-yil", rows, totals)
    }

    private fun countByType(txs: List<TransactionEntity>): Map<String, Double> {
        val m = mutableMapOf("a" to 0.0, "b" to 0.0, "c" to 0.0, "d" to 0.0, "k" to 0.0)
        for (tx in txs) {
            val t = tx.type.lowercase()
            if (t in cargoTypes) m[t] = (m[t] ?: 0.0) + tx.amount
        }
        return m
    }
}
