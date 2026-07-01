// VERSIYA-MARKER-v4-cpPrice
package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.RasxodDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.dao.YukNarxDao
import uz.daftar.app.data.db.dao.ClientPriceDao
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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
    val revenue: Long,                   // jami daromad (N narx — sotilgan)
    val tCost: Long,                     // ulgurji tannarx (T narx)
    val grossProfit: Long,               // yalpi foyda = N − T
    val payments: Long,                  // jami P
    val expenses: Long,                  // rasxod
    val yukRasxodi: Long,                // yuk rasxodi = sotilgan miqdor × narx (sana bo'yicha)
    val profit: Long,                    // sof foyda = (N − T) − rasxod − yuk rasxodi
    val transactionCount: Int,
    val clientCount: Int
)

class GetDailyReportUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao,
    private val yukDao: YukNarxDao,
    private val rasxodDao: RasxodDao,
    private val clientPriceDao: ClientPriceDao
) {
    suspend operator fun invoke(userId: Long, date: LocalDate): PeriodReport {
        val start = date.atStartOfDay().format(ISO)
        val end = date.plusDays(1).atStartOfDay().format(ISO)
        val txs = txDao.getRange(userId, start, end)
        val rasxodTotal = (rasxodDao.getTotalRange(userId, start, end) ?: 0.0).roundToLong()
        val report = buildReport(userId, txs, yukDao, priceDao, clientPriceDao, rasxodTotal)
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
    private val rasxodDao: RasxodDao,
    private val clientPriceDao: ClientPriceDao
) {
    suspend operator fun invoke(userId: Long, year: Int, month: Int): PeriodReport {
        val ym = YearMonth.of(year, month)
        val s = ym.atDay(1).atStartOfDay().format(GetDailyReportUseCase.ISO)
        val e = ym.atEndOfMonth().plusDays(1).atStartOfDay().format(GetDailyReportUseCase.ISO)
        val txs = txDao.getRange(userId, s, e)
        val rasxodTotal = (rasxodDao.getTotalRange(userId, s, e) ?: 0.0).roundToLong()
        val report = buildReport(userId, txs, yukDao, priceDao, clientPriceDao, rasxodTotal)
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
    private val rasxodDao: RasxodDao,
    private val clientPriceDao: ClientPriceDao
) {
    suspend operator fun invoke(userId: Long, year: Int): PeriodReport {
        val s = LocalDate.of(year, 1, 1).atStartOfDay().format(GetDailyReportUseCase.ISO)
        val e = LocalDate.of(year + 1, 1, 1).atStartOfDay().format(GetDailyReportUseCase.ISO)
        val txs = txDao.getRange(userId, s, e)
        val rasxodTotal = (rasxodDao.getTotalRange(userId, s, e) ?: 0.0).roundToLong()
        val report = buildReport(userId, txs, yukDao, priceDao, clientPriceDao, rasxodTotal)
        return report.copy(
            title = "Yillik hisobot",
            rangeLabel = "$year yil"
        )
    }
}

// ───────── "Shu oy qarz" — joriy oy bo'yicha har mijoz: yuk(N), to'lov, qoldi ─────────
data class MonthClientDebt(
    val client: String,
    val yuk: Long,    // shu oyda olingan yuk (N narx qiymati)
    val tolov: Long,  // shu oyda to'langan (P)
    val qoldi: Long   // shu oy qoldig'i = yuk − tolov
)

data class MonthDebtReport(
    val rangeLabel: String,
    val rows: List<MonthClientDebt>,
    val totalYuk: Long,
    val totalTolov: Long,
    val totalQoldi: Long
)

class GetMonthClientDebtUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao,
    private val yukDao: YukNarxDao,
    private val clientPriceDao: ClientPriceDao
) {
    suspend operator fun invoke(userId: Long, year: Int, month: Int): MonthDebtReport {
        val ym = YearMonth.of(year, month)
        val s = ym.atDay(1).atStartOfDay().format(GetDailyReportUseCase.ISO)
        val e = ym.atEndOfMonth().plusDays(1).atStartOfDay().format(GetDailyReportUseCase.ISO)
        val txs = txDao.getRange(userId, s, e)
        val cargo = setOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
        val map = LinkedHashMap<String, DoubleArray>() // [yuk, tolov]
        for (tx in txs) {
            val type = TxType.fromCode(tx.type) ?: continue
            val arr = map.getOrPut(tx.clientName) { DoubleArray(2) }
            when (type) {
                TxType.P -> arr[1] += tx.amount
                TxType.Q -> arr[0] += tx.amount
                in cargo -> {
                    val n = findPriceForReport(userId, tx.clientName, tx.type, tx.date, priceDao, yukDao, clientPriceDao)
                        ?: tx.tOverride
                    if (n != null) arr[0] += tx.amount * n
                }
                else -> {}
            }
        }
        val rows = map.map { (c, a) ->
            // YAXLITLASH: qoldi = ko'rinadigan (roundlangan) yuk − to'lov, shunda qatorlar to'g'ri qo'shiladi (−1 chiqmaydi)
            val yk = a[0].roundToLong()
            val tl = a[1].roundToLong()
            MonthClientDebt(c, yk, tl, yk - tl)
        }.filter { it.yuk != 0L || it.tolov != 0L }
            .sortedByDescending { it.qoldi }
        return MonthDebtReport(
            rangeLabel = "${MONTH_UZ[month - 1]} $year",
            rows = rows,
            totalYuk = rows.sumOf { it.yuk },
            totalTolov = rows.sumOf { it.tolov },
            totalQoldi = rows.sumOf { it.qoldi }
        )
    }
}

/** Yagona algoritm — har period uchun hisobotni qurish (N daromad, T tannarx, foyda). */
internal suspend fun buildReport(
    userId: Long,
    txs: List<TransactionEntity>,
    yukDao: YukNarxDao,
    priceDao: PriceHistoryDao,
    clientPriceDao: ClientPriceDao,
    rasxodTotal: Long
): PeriodReport {
    val totals = mutableMapOf<TxType, Double>()
    var revenue = 0.0
    var tCost = 0.0
    var payments = 0.0
    var yukRasxodi = 0.0
    val clientNames = mutableSetOf<String>()

    // Global T narx TARIXI (har yozuv sanasiga qarab) — turlar bo'yicha
    val cargo = setOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
    val tHist = mutableMapOf<String, List<Pair<String, Double>>>()
    val t1Hist = mutableMapOf<String, List<Pair<String, Double>>>()
    val yrHist = mutableMapOf<String, List<Pair<String, Double>>>()
    run {
        val allT = yukDao.getAllGlobalGroup(userId, "t").groupBy { it.type.lowercase() }
        val allT1 = yukDao.getAllGlobalGroup(userId, "t1").groupBy { it.type.lowercase() }
        val allYr = yukDao.getAllGlobalGroup(userId, "yr").groupBy { it.type.lowercase() }
        for (t in cargo) {
            val code = t.code.lowercase()
            tHist[code] = (allT[code] ?: emptyList()).map { it.date to it.price }.sortedBy { it.first }
            t1Hist[code] = (allT1[code] ?: emptyList()).map { it.date to it.price }.sortedBy { it.first }
            yrHist[code] = (allYr[code] ?: emptyList()).map { it.date to it.price }.sortedBy { it.first }
        }
    }
    // PRELOAD: N narx tarixi + mijoz joriy narxlari — BIR MARTA (N+1 emas)
    val phByKey = priceDao.getAllForUser(userId)
        .groupBy { it.clientName.lowercase() to it.priceType.lowercase() }
        .mapValues { (_, v) -> v.sortedWith(compareBy({ it.date }, { it.id })) }
    val cpByName = clientPriceDao.getAllForUser(userId).associateBy { it.clientName.lowercase() }

    // findPriceForReport bilan AYNAN bir xil mantiq — lekin xotirada
    fun nPriceFor(clientName: String, type: String, date: String): Double? {
        val dayEnd = date.take(10) + " 23:59:59"
        val list = phByKey[clientName.lowercase() to type.lowercase()]
        if (!list.isNullOrEmpty()) {
            val at = list.lastOrNull { it.date <= dayEnd }?.price
            if (at != null) return at
            val next = list.firstOrNull { it.date > dayEnd }?.price
            if (next != null) return next
        }
        val cp = cpByName[clientName.lowercase()]
        val cpPrice = when (type.lowercase()) {
            "a" -> cp?.aPrice
            "b" -> cp?.bPrice
            "c" -> cp?.cPrice
            "d" -> cp?.dPrice
            "k" -> cp?.kPrice
            else -> null
        }
        if (cpPrice != null && cpPrice > 0.0) return cpPrice
        // BOT bilan moslik: N narx topilmasa — daromadga QO'SHILMAYDI (global T'ga fallback YO'Q)
        return null
    }

    // Berilgan sana (kun) yoki undan oldingi eng oxirgi global narx
    fun globalAt(list: List<Pair<String, Double>>?, date: String): Double? {
        if (list.isNullOrEmpty()) return null
        val day = date.take(10)
        var best: Double? = null
        for ((d, p) in list) { if (d.take(10) <= day) best = p else break }
        return best ?: list.first().second
    }
    // Yuk rasxodi narxi — sana bo'yicha; narx YO'Q bo'lsa 0 (fallback yo'q → eski yozuvga tushmaydi)
    fun yrAt(list: List<Pair<String, Double>>?, date: String): Double {
        if (list.isNullOrEmpty()) return 0.0
        val day = date.take(10)
        var best = 0.0; var found = false
        for ((d, p) in list) { if (d.take(10) <= day) { best = p; found = true } else break }
        return if (found) best else 0.0
    }

    for (tx in txs) {
        val type = TxType.fromCode(tx.type) ?: continue
        totals[type] = (totals[type] ?: 0.0) + tx.amount
        clientNames.add(tx.clientName.lowercase())

        when (type) {
            TxType.P -> payments += tx.amount
            TxType.Q -> { /* qo'lda qarz — sotuv emas; daromad/foydaga KIRMAYDI, faqat qarzga ta'sir qiladi */ }
            in cargo -> {
                // Sotilgan narx (N): avval N (mijoz uchun), bo'lmasa T (global)
                // BOT bilan moslik: daromadga FAQAT N narx (topilmasa qo'shilmaydi)
                val nPrice = nPriceFor(tx.clientName, tx.type, tx.date)
                if (nPrice != null) revenue += tx.amount * nPrice
                // Ulgurji tannarx: bir martalik override, yoki T1 tarif, yoki global T (O'SHA SANADAGI)
                val globalCost = if (tx.costTier == "t1")
                    (globalAt(t1Hist[tx.type.lowercase()], tx.date) ?: globalAt(tHist[tx.type.lowercase()], tx.date))
                else globalAt(tHist[tx.type.lowercase()], tx.date)
                val tPrice = tx.tOverride ?: globalCost
                if (tPrice != null) tCost += tx.amount * tPrice
                // Yuk rasxodi: O'SHA SANADAGI narx × miqdor (narx yo'q = 0, eski yozuvga tushmaydi)
                yukRasxodi += tx.amount * yrAt(yrHist[tx.type.lowercase()], tx.date)
            }
            else -> { /* boshqa turlar e'tibordan chiqarilgan */ }
        }
    }

    // YAXLITLASH: grossProfit = ko'rinadigan (roundlangan) revenue − tCost, shunda mos qo'shiladi
    val revLong = revenue.roundToLong()
    val tCostLong = tCost.roundToLong()
    val grossLong = revLong - tCostLong
    val yrLong = yukRasxodi.roundToLong()
    return PeriodReport(
        title = "",
        rangeLabel = "",
        totals = totals,
        revenue = revLong,
        tCost = tCostLong,
        grossProfit = grossLong,
        payments = payments.roundToLong(),
        expenses = rasxodTotal,
        yukRasxodi = yrLong,
        profit = grossLong - rasxodTotal - yrLong,
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
    yukDao: YukNarxDao,
    clientPriceDao: ClientPriceDao
): Double? {
    // Kun darajasida: shu kunning oxirigacha — bugun qo'yilgan narx bugungi yuklarga ham tegishli
    val dayEnd = date.take(10) + " 23:59:59"
    // 1) Client uchun N narx (price_history)
    val nPrice = priceDao.getPriceAt(userId, clientName, type, dayEnd)
    if (nPrice != null) return nPrice
    val nNext = priceDao.getNextPrice(userId, clientName, type, dayEnd)
    if (nNext != null) return nNext
    // 2) Mijozning JORIY narxi (client_prices) — global T dan OLDIN
    val cp = runCatching { clientPriceDao.get(userId, clientName) }.getOrNull()
    val cpPrice = when (type.lowercase()) {
        "a" -> cp?.aPrice
        "b" -> cp?.bPrice
        "c" -> cp?.cPrice
        "d" -> cp?.dPrice
        "k" -> cp?.kPrice
        else -> null
    }
    if (cpPrice != null && cpPrice > 0.0) return cpPrice
    // 3) Global T narx
    val tEntry = yukDao.getLatestGlobal(userId, type, "t") ?: return null
    return tEntry.price
}

internal val MONTH_UZ = listOf(
    "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
    "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr"
)

// ───────── Mijoz foydasi (oylik / yillik) ─────────
data class ClientProfitReport(
    val client: String,
    val year: Int,
    val monthly: List<Pair<String, Long>>,  // (oy nomi, foyda) — joriy yil, nolik bo'lmagan
    val yearly: List<Pair<Int, Long>>,       // (yil, foyda)
    val totalThisYear: Long
)

class GetClientProfitUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao,
    private val yukDao: YukNarxDao,
    private val clientPriceDao: ClientPriceDao
) {
    suspend operator fun invoke(userId: Long, clientName: String): ClientProfitReport {
        val cn = clientName.lowercase()
        val txs = txDao.getByClient(userId, cn)
        val cargo = setOf("a", "b", "c", "d", "k")
        val tHist = mutableMapOf<String, List<Pair<String, Double>>>()
        val t1Hist = mutableMapOf<String, List<Pair<String, Double>>>()
        run {
            val allT = yukDao.getAllGlobalGroup(userId, "t").groupBy { it.type.lowercase() }
            val allT1 = yukDao.getAllGlobalGroup(userId, "t1").groupBy { it.type.lowercase() }
            for (t in cargo) {
                tHist[t] = (allT[t] ?: emptyList()).map { it.date to it.price }.sortedBy { it.first }
                t1Hist[t] = (allT1[t] ?: emptyList()).map { it.date to it.price }.sortedBy { it.first }
            }
        }
        fun globalAt(list: List<Pair<String, Double>>?, date: String): Double? {
            if (list.isNullOrEmpty()) return null
            val day = date.take(10)
            var best: Double? = null
            for ((d, p) in list) { if (d.take(10) <= day) best = p else break }
            return best ?: list.first().second
        }

        val nowYear = LocalDate.now().year
        val byMonth = DoubleArray(12)        // joriy yil oylik foyda
        val byYear = sortedMapOf<Int, Double>(compareByDescending { it })

        for (tx in txs) {
            val type = TxType.fromCode(tx.type) ?: continue
            val d = runCatching { LocalDateTime.parse(tx.date, GetDailyReportUseCase.ISO).toLocalDate() }.getOrNull() ?: continue
            val profit: Double = when (tx.type) {
                "p" -> 0.0
                "q" -> 0.0  // qo'lda qarz — foyda emas
                in cargo -> {
                    val n = findPriceForReport(userId, tx.clientName, tx.type, tx.date, priceDao, yukDao, clientPriceDao) ?: tx.tOverride
                    val globalCost = if (tx.costTier == "t1") (globalAt(t1Hist[tx.type], tx.date) ?: globalAt(tHist[tx.type], tx.date)) else globalAt(tHist[tx.type], tx.date)
                    val t = tx.tOverride ?: globalCost
                    if (n != null && t != null) tx.amount * (n - t)
                    else 0.0
                }
                else -> 0.0
            }
            if (profit == 0.0) continue
            byYear[d.year] = (byYear[d.year] ?: 0.0) + profit
            if (d.year == nowYear) byMonth[d.monthValue - 1] += profit
        }

        val monthly = (0 until 12)
            .filter { byMonth[it] != 0.0 }
            .map { MONTH_UZ[it] to byMonth[it].roundToLong() }
        val yearly = byYear.map { (y, v) -> y to v.roundToLong() }
        return ClientProfitReport(
            client = clientName,
            year = nowYear,
            monthly = monthly,
            yearly = yearly,
            totalThisYear = byMonth.sum().roundToLong()
        )
    }
}

// ───────── Qarz eslatma — muddati o'tgan qarzdorlar (yoshi bo'yicha) ─────────
data class OverdueDebtor(
    val client: String,
    val debt: Long,
    val daysOverdue: Int,                // oxirgi to'lovdan (yo'q bo'lsa qarz boshidan) beri kunlar
    val sinceDate: LocalDate             // sanash boshlangan sana
)

class GetOverdueDebtorsUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao
) {
    // TEZLIK/KESH: ochilganda getOverdue 2-3 marta chaqiriladi (eslatma + karta + bosh ekran).
    // Qisqa kesh shu burst'ni birlashtiradi — ko'p tranzaksiyada ham qayta-qayta hisoblanmaydi.
    @Volatile private var cache: List<OverdueDebtor>? = null
    @Volatile private var cacheUser: Long = -1L
    @Volatile private var cacheAt: Long = 0L
    private val cacheTtlMs = 1200L

    /** Yozuv qo'shilgach/o'chgach chaqiriladi — keyingi so'rov yangidan hisoblaydi. */
    fun invalidate() { cache = null }

    suspend operator fun invoke(userId: Long): List<OverdueDebtor> {
        val nowMs = System.currentTimeMillis()
        cache?.let { if (cacheUser == userId && nowMs - cacheAt < cacheTtlMs) return it }
        val today = LocalDate.now()
        // TEZLIK: har mijoz uchun alohida so'rov o'rniga — HAMMASI 2 ta so'rovda
        val allTxs = txDao.getRange(userId, "2000-01-01", today.plusDays(1).toString())
        val txByClient = allTxs.groupBy { it.clientName.lowercase() }   // getRange allaqachon date bo'yicha tartibli
        val pricesByClient = priceDao.getAllForUser(userId)
            .groupBy { it.clientName.lowercase() }
            .mapValues { (_, l) -> l.sortedBy { it.date }.groupBy { it.priceType } }
        val out = mutableListOf<OverdueDebtor>()
        for ((cn, txs) in txByClient) {
            if (txs.isEmpty()) continue
            val name = txs.first().clientName
            val pricesByType = pricesByClient[cn] ?: emptyMap()
            // Pure hisob — DebtMath (unit test bilan qoplangan)
            val cb = DebtMath.clientBalance(txs, pricesByType, today) ?: continue
            out.add(OverdueDebtor(name, cb.balance.roundToLong(), cb.days, cb.since))
        }
        val result = out.sortedByDescending { it.daysOverdue }
        cache = result; cacheUser = userId; cacheAt = System.currentTimeMillis()
        return result
    }

}

/**
 * Qarz hisobining SOF (pure) mantiqi — DB'siz, unit test bilan qoplangan.
 * GetOverdueDebtorsUseCase shu yerdan foydalanadi (mantiq AYNAN bir xil).
 */
data class ClientBalance(val balance: Double, val since: LocalDate, val days: Int)

object DebtMath {
    /** Berilgan sanadagi (yoki undan oldingi eng yaqin) narx. prices sana bo'yicha tartiblangan. */
    fun priceAt(prices: List<uz.daftar.app.data.db.entity.PriceHistoryEntity>?, at: String): Double? {
        if (prices.isNullOrEmpty()) return null
        var best: uz.daftar.app.data.db.entity.PriceHistoryEntity? = null
        for (p in prices) {
            if (p.date.take(10) <= at.take(10)) best = p else break
        }
        return best?.price ?: prices.firstOrNull()?.price
    }

    /**
     * Bitta mijozning yakuniy qarzi (running balance). txs sana bo'yicha tartiblangan.
     * Qarz yo'q (<= 0.5) bo'lsa null. p=to'lov(-), q=qo'lda qarz(+), a/b/c/d/k=yuk(+miqdor×narx).
     */
    fun clientBalance(
        txs: List<uz.daftar.app.data.db.entity.TransactionEntity>,
        pricesByType: Map<String, List<uz.daftar.app.data.db.entity.PriceHistoryEntity>>,
        today: LocalDate
    ): ClientBalance? {
        var bal = 0.0
        var firstDebtDate: LocalDate? = null    // qarz 0'dan musbatga o'tgan sana
        var lastPaymentDate: LocalDate? = null   // oxirgi to'lov (P) sanasi
        for (tx in txs) {
            val before = bal
            val d = runCatching {
                LocalDateTime.parse(tx.date, GetDailyReportUseCase.ISO).toLocalDate()
            }.getOrNull()
            when (tx.type) {
                "p" -> { bal -= tx.amount; if (d != null) lastPaymentDate = d }
                "q" -> bal += tx.amount
                else -> {
                    val pr = tx.tOverride ?: priceAt(pricesByType[tx.type], tx.date)
                    if (pr != null) bal += tx.amount * pr
                }
            }
            if (before <= 0.0 && bal > 0.0 && d != null) firstDebtDate = d
            if (bal <= 0.0) firstDebtDate = null
        }
        if (bal <= 0.5) return null
        val fdd = firstDebtDate
        val lpd = lastPaymentDate
        val since = when {
            fdd != null && lpd != null -> maxOf(fdd, lpd)
            fdd != null -> fdd
            lpd != null -> lpd
            else -> return null
        }
        val days = ChronoUnit.DAYS.between(since, today).toInt().coerceAtLeast(0)
        return ClientBalance(bal, since, days)
    }
}
