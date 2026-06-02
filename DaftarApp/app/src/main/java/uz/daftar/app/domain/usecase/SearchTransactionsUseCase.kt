package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Tranzaksiyalarni qidirish: matn bilan, sana bilan, mijoz bilan.
 * Bot.py'dagi qidir buyrug'iga ekvivalent.
 */
class SearchTransactionsUseCase @Inject constructor(
    private val txDao: TransactionDao
) {
    /** Sana oralig'i bilan qidirish */
    suspend fun byDateRange(
        userId: Long,
        from: LocalDate,
        toInclusive: LocalDate
    ): List<TransactionEntity> {
        val s = from.atStartOfDay().format(ISO)
        val e = toInclusive.plusDays(1).atStartOfDay().format(ISO)
        return txDao.getRange(userId, s, e)
    }

    /** Bitta sana bo'yicha */
    suspend fun byDate(userId: Long, date: LocalDate): List<TransactionEntity> =
        byDateRange(userId, date, date)

    /** Mijoz nomi bo'yicha (qisman) */
    suspend fun byClientName(userId: Long, query: String): List<TransactionEntity> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        // Texnik cheklovi: oddiy LIKE qidiruv. To'liq FTS keyingi versiyada.
        val all = txDao.getAllClientNames(userId).filter { it.contains(q) }
        val result = mutableListOf<TransactionEntity>()
        for (cn in all) {
            result.addAll(txDao.getByClient(userId, cn))
        }
        return result.sortedByDescending { it.date }
    }

    /** Tur va sana oralig'i bilan kombinatsiyalashgan qidiruv */
    suspend fun byTypeAndRange(
        userId: Long,
        type: TxType,
        from: LocalDate,
        toInclusive: LocalDate
    ): List<TransactionEntity> {
        return byDateRange(userId, from, toInclusive).filter { it.type == type.code }
    }

    companion object {
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
