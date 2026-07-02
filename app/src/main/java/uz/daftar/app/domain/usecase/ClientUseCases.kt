package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.TransactionEntity
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Bitta mijozning butun tarixi (oylar bo'yicha).
 * Bot.py'dagi mijoz_tarix funksiyasiga ekvivalent.
 */
class GetClientHistoryUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val calculateDebt: CalculateDebtUseCase
) {
    suspend operator fun invoke(userId: Long, clientName: String): ClientHistory {
        val cn = clientName.lowercase()
        val txs = txDao.getByClient(userId, cn).sortedBy { it.date }
        val debt = calculateDebt(userId, cn)
        return ClientHistory(
            name = cn,
            debt = debt,
            transactions = txs
        )
    }
}

/**
 * Barcha mijozlar — qarz va oxirgi yozuv sanasi bilan.
 * Mijozlar ekrani uchun.
 */
class GetAllClientsUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: uz.daftar.app.data.db.dao.PriceHistoryDao
) {
    // OPTIMALLASHTIRILDI: avval har mijozga 4 so'rov (N+1) -> endi JAMI 2 so'rov.
    suspend operator fun invoke(userId: Long): List<ClientSummary> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val allTx = txDao.getAllForUser(userId)
            val allPrices = priceDao.getAllForUser(userId)
            val pricesByClient = allPrices.groupBy { it.clientName.lowercase() }
                .mapValues { (_, l) -> l.groupBy { it.priceType }.mapValues { e -> e.value.sortedBy { it.date } } }
            allTx.groupBy { it.clientName.lowercase() }.map { (cn, txs) ->
                val pbt = pricesByClient[cn] ?: emptyMap()
                var debt = 0.0
                var lastYuk: String? = null
                var lastPay: String? = null
                for (tx in txs) {
                    when (tx.type) {
                        uz.daftar.app.domain.model.TxType.P.code -> {
                            debt -= tx.amount
                            if (lastPay == null || tx.date > lastPay!!) lastPay = tx.date
                        }
                        uz.daftar.app.domain.model.TxType.Q.code -> debt += tx.amount
                        else -> {
                            val price = findPriceFor(pbt[tx.type], tx.date)
                            if (price != null) debt += (tx.amount * price).roundToLong()
                            if (lastYuk == null || tx.date > lastYuk!!) lastYuk = tx.date
                        }
                    }
                }
                ClientSummary(
                    cn, debt.roundToLong(), lastYuk, lastPay,
                    topType = txs.filter { it.type.lowercase() in setOf("a", "b", "c", "d", "k") }
                        .groupBy { it.type.lowercase() }
                        .maxByOrNull { (_, l) -> l.sumOf { it.amount } }?.key
                )
            }.sortedByDescending { it.debt }
        }

    private fun findPriceFor(prices: List<uz.daftar.app.data.db.entity.PriceHistoryEntity>?, at: String): Double? {
        if (prices.isNullOrEmpty()) return null
        var best: uz.daftar.app.data.db.entity.PriceHistoryEntity? = null
        for (p in prices) { if (p.date.take(10) <= at.take(10)) best = p else break }
        return best?.price ?: prices.firstOrNull()?.price
    }
}

data class ClientHistory(
    val name: String,
    val debt: Long,
    val transactions: List<TransactionEntity>
)

data class ClientSummary(
    val name: String,
    val debt: Long,
    val lastYukDate: String?,
    val lastPaymentDate: String?,
    val topType: String? = null   // asosiy (eng ko'p) yuk turi kodi — rang uchun
)
