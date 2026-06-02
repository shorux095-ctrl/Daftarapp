package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.TransactionEntity
import javax.inject.Inject

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
    private val calculateDebt: CalculateDebtUseCase
) {
    suspend operator fun invoke(userId: Long): List<ClientSummary> {
        val names = txDao.getAllClientNames(userId)
        return names.map { cn ->
            val debt = calculateDebt(userId, cn)
            val lastYuk = txDao.getLastYukDate(userId, cn)
            val lastPay = txDao.getLastPaymentDate(userId, cn)
            ClientSummary(
                name = cn,
                debt = debt,
                lastYukDate = lastYuk,
                lastPaymentDate = lastPay
            )
        }.sortedByDescending { it.debt }
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
    val lastPaymentDate: String?
)
