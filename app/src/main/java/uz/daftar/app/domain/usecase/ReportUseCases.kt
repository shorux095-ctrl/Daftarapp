package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.RasxodDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.dao.YukNarxDao
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Bot.py'dagi rep_daily / rep_monthly / rep_yearly funksiyalari Kotlin'da.
 * Har bir period uchun:
 *   - Yuk yig'indilari (A, B, C, D, K) tonna/qop bo'yicha
 *   - P (to'lov) jami
 *   - Q (qarz) jami
 *   - Daromad (revenue) — yuk × narx
 *   - Rasxod jami
 *   - Sof foyda (revenue - rasxod)
 */
data class PeriodReport(
    val title: String,
    val rangeLabel: String,
    val totals: Map<TxType, Double>,    // har turi bo'yicha jami miqdor
    val revenue: Long,                   // jami daromad
    val payments: Long,                  // jami P
    val expenses: Long,                  // rasxod
    val profit: Long,                    // sof foyda
    val transactionCount: Int,
    val clientCount: Int
)

class GetDailyReportUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao,
    private val yukDao: YukNarxDao,
    private val rasxodDao: RasxodDao
) {
    suspend operator fun invoke(userId: Long, date: LocalDate): PeriodReport {
        val start = date.atStartOfDay().format(ISO)
        val end = date.plusDays(1).atStartOfDay().format(ISO)
        val txs = txDao.getRange(userId, start, end)
        val rasxodTotal = (rasxodDao.getTotalRange(userId, start, end) ?: 0.0).roundToLong()
        val report = buildReport(userId, txs, yukDao, priceDao, rasxodTotal)
        return report.copy(
            title = "Kunlik hisobot",
            rangeLabel = date.format(LABEL_DATE)
        )
    }

    companion object {
        internal val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        internal val LABEL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")
    }
}

class GetMonthlyReportUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao,
    private val yukDao: YukNarxDao,
    private val rasxodDao: RasxodDao
) {
    suspend operator fun invoke(userId: Long, year: Int, month: Int): PeriodReport {
        val ym = YearMonth.of(year, month)
        val s = ym.atDay(1).atStartOfDay().format(GetDailyReportUseCase.ISO)
        val e = ym.atEndOfMonth().plusDays(1).atStartOfDay().format(GetDailyReportUseCase.ISO)
        val txs = txDao.getRange(userId, s, e)
        val rasxodTotal = (rasxodDao.getTotalRange(userId, s, e) ?: 0.0).roundToLong()
        val report = buildReport(userId, txs, yukDao, priceDao, rasxodTotal)
        return report.copy(
            title = "Oylik hisobot",
            rangeLabel = "${MONTH_UZ[month - 1]} $year"
        )
    }
}

class GetYearlyReportUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao,
    private val yukDao: YukNarxDao,
    private val rasxodDao: RasxodDao
) {
    suspend operator fun invoke(userId: Long, year: Int): PeriodReport {
        val s = LocalDate.of(year, 1, 1).atStartOfDay().format(GetDailyReportUseCase.ISO)
        val e = LocalDate.of(year + 1, 1, 1).atStartOfDay().format(GetDailyReportUseCase.ISO)
        val txs = txDao.getRange(userId, s, e)
        val rasxodTotal = (rasxodDao.getTotalRange(userId, s, e) ?: 0.0).roundToLong()
        val report = buildReport(userId, txs, yukDao, priceDao, rasxodTotal)
        return report.copy(
            title = "Yillik hisobot",
            rangeLabel = "$year yil"
        )
    }
}

/**
 * Yagona algoritm — har period uchun hisobotni qurish.
 * Daromad = sum(yuk_amount × T_narx_at_date) — N narx mijozga, T narx default.
 */
internal suspend fun buildReport(
    userId: Long,
    txs: List<TransactionEntity>,
    yukDao: YukNarxDao,
    priceDao: PriceHistoryDao,
    rasxodTotal: Long
): PeriodReport {
    val totals = mutableMapOf<TxType, Double>()
    var revenue = 0.0
    var payments = 0.0
    val clientNames = mutableSetOf<String>()

    for (tx in txs) {
        val type = TxType.fromCode(tx.type) ?: continue
        totals[type] = (totals[type] ?: 0.0) + tx.amount
        clientNames.add(tx.clientName.lowercase())

        when (type) {
            TxType.P -> payments += tx.amount
            TxType.Q -> revenue += tx.amount  // qo'lda qarz ham revenue qismi
            in setOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K) -> {
                // Narx: avval N (mijoz uchun), bo'lmasa T (global)
                val price = findPriceForReport(
                    userId, tx.clientName, tx.type, tx.date, priceDao, yukDao
                ) ?: tx.tOverride
                if (price != null) revenue += tx.amount * price
            }
            else -> { /* boshqa turlar e'tibordan chiqarilgan */ }
        }
    }

    return PeriodReport(
        title = "",
        rangeLabel = "",
        totals = totals,
        revenue = revenue.roundToLong(),
        payments = payments.roundToLong(),
        expenses = rasxodTotal,
        profit = revenue.roundToLong() - rasxodTotal,
        transactionCount = txs.size,
        clientCount = clientNames.size
    )
}

private suspend fun findPriceForReport(
    userId: Long,
    clientName: String,
    type: String,
    date: String,
    priceDao: PriceHistoryDao,
    yukDao: YukNarxDao
): Double? {
    // 1) Client uchun N narx
    val nPrice = priceDao.getPriceAt(userId, clientName, type, date)
    if (nPrice != null) return nPrice
    val nNext = priceDao.getNextPrice(userId, clientName, type, date)
    if (nNext != null) return nNext
    // 2) Global T narx
    val tEntry = yukDao.getLatestGlobal(userId, type, "t") ?: return null
    return tEntry.price
}

internal val MONTH_UZ = listOf(
    "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
    "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr"
)
