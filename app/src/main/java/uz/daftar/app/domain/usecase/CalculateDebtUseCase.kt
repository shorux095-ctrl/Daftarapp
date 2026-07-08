package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.PriceHistoryEntity
import uz.daftar.app.domain.model.TxType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Bir mijozning qarzini hisoblash.
 *
 * Bot.py'dagi calculate_debt mantiqi — batch query bilan optimallashtirilgan
 * (N+1 query muammosini hal qiladi: barcha narxlarni bir marta olib, xotirada
 * mos keladigan narxni qidiramiz).
 *
 * Mantiq:
 *   P (to'lov)        → qarzdan ayriladi
 *   Q (qarz qoldig'i) → qarzga qo'shiladi (narxsiz)
 *   A/B/C/D/K (yuk)   → o'sha sanadagi N narx × miqdor → qarzga qo'shiladi
 *   t_override (t?)   → o'sha yozuv uchun maxsus narx
 */
class CalculateDebtUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao,
    private val clientPriceDao: uz.daftar.app.data.db.dao.ClientPriceDao
) {
    suspend operator fun invoke(userId: Long, clientName: String): Long {
        val cn = clientName.lowercase()

        // 1) Mijozning barcha tranzaksiyalari
        val txs = txDao.getByClient(userId, cn)
        if (txs.isEmpty()) return 0

        // 2) Mijozning barcha narx tarixi — bir marta olamiz
        val allPrices = priceDao.getAllForClient(userId, cn)
        val pricesByType: Map<String, List<PriceHistoryEntity>> =
            allPrices.groupBy { it.priceType }
                .mapValues { e -> e.value.sortedBy { it.date } }
        // v152: price_history'da yo'q tur uchun client_prices fallback —
        // endi Qarzdorlar bilan kunlik hisobot BIR XIL raqam ko'rsatadi
        val cp = runCatching { clientPriceDao.get(userId, cn) }.getOrNull()
        fun fallbackPrice(type: String): Double? = when (type) {
            "a" -> cp?.aPrice; "b" -> cp?.bPrice; "c" -> cp?.cPrice
            "d" -> cp?.dPrice; "k" -> cp?.kPrice; else -> null
        }

        // 3) Har tranzaksiya uchun qarzga qo'shamiz/ayiramiz
        var debt = 0.0
        for (tx in txs) {
            when (tx.type) {
                TxType.P.code -> debt -= tx.amount
                TxType.Q.code -> debt += tx.amount
                else -> {
                    // QARZ = N narx (SOTUV narxi). tOverride (tannarx/T) QARZDA ISHLATILMAYDI!
                    // tannarx faqat foyda hisobida kerak, qarzda emas.
                    val price = findPriceFor(pricesByType[tx.type], tx.date)
                        ?: fallbackPrice(tx.type)
                    if (price != null) {
                        debt += (tx.amount * price).roundToLong()
                    }
                }
            }
        }
        return debt.roundToLong()
    }

    /** Berilgan sanada (yoki undan oldin) eng oxirgi narx. Topilmasa - keyingi eng yaqin. */
    private fun findPriceFor(prices: List<PriceHistoryEntity>?, atDateStr: String): Double? {
        if (prices.isNullOrEmpty()) return null
        // O'sha sana va o'tgan narxlar orasidan eng oxirgisi
        var bestBefore: PriceHistoryEntity? = null
        for (p in prices) {
            if (compareDates(p.date, atDateStr) <= 0) {
                bestBefore = p
            } else {
                break
            }
        }
        if (bestBefore != null) return bestBefore.price
        // Keyingi eng yaqin (retroaktiv yozuv uchun)
        return prices.firstOrNull()?.price
    }

    /** ISO sana stringlarini KUN darajasida taqqoslash (soat e'tiborga olinmaydi).
     *  Shunda bugun qo'yilgan narx bugungi (oldinroq soatdagi) yuklarga ham tatbiq etiladi. */
    private fun compareDates(a: String, b: String): Int = a.take(10).compareTo(b.take(10))
}
