package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.DeletedTransactionDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.DeletedTransactionEntity
import uz.daftar.app.data.db.entity.TransactionEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Karzina (deleted_transactions) — 7 kun ichida tiklash mumkin.
 * Bot.py'dagi /tk<id> orqali tiklash bilan bir xil mantiq.
 */
class GetDeletedTransactionsUseCase @Inject constructor(
    private val dao: DeletedTransactionDao
) {
    suspend operator fun invoke(userId: Long): List<DeletedTransactionEntity> = dao.getAll(userId)
}

class RestoreTransactionUseCase @Inject constructor(
    private val deletedDao: DeletedTransactionDao,
    private val txDao: TransactionDao
) {
    suspend operator fun invoke(deletedId: Long): Boolean {
        val deleted = deletedDao.getById(deletedId) ?: return false
        // Asl jadvalga qaytarish
        txDao.insert(
            TransactionEntity(
                userId = deleted.userId,
                clientName = deleted.clientName,
                type = deleted.type,
                amount = deleted.amount,
                date = deleted.date,
                tOverride = null
            )
        )
        // Karzinadan o'chirish
        deletedDao.deleteById(deletedId)
        return true
    }
}

class PurgeKarzinaUseCase @Inject constructor(
    private val deletedDao: DeletedTransactionDao
) {
    /** 7 kundan oldingilarni butunlay o'chirish — auto-cleanup. */
    suspend operator fun invoke(userId: Long): Int {
        val cutoff = LocalDateTime.now(ZONE).minusDays(7).format(ISO)
        return deletedDao.deleteOlderThan(userId, cutoff)
    }

    companion object {
        private val ZONE = ZoneId.of("Asia/Tashkent")
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

/**
 * Yangilangan DeleteTransaction — endi yozuvni karzinaga ko'chiradi.
 * (EditDeleteUseCase.kt'da eski DeleteTransactionUseCase ham bor — ikkalasi parallel ishlaydi.)
 */
class DeleteToKarzinaUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val deletedDao: DeletedTransactionDao
) {
    suspend operator fun invoke(tx: TransactionEntity): Boolean {
        // Karzinaga ko'chirish
        deletedDao.insert(
            DeletedTransactionEntity(
                userId = tx.userId,
                clientName = tx.clientName,
                type = tx.type,
                amount = tx.amount,
                date = tx.date,
                deletedAt = LocalDateTime.now(ZONE).format(ISO),
                note = ""
            )
        )
        // Asl jadvaldan o'chirish
        txDao.deleteById(tx.id)
        return true
    }

    /** id orqali — yozuvni topib, karzinaga ko'chiradi. */
    suspend operator fun invoke(userId: Long, id: Long): Boolean {
        val tx = txDao.getById(id) ?: return false
        return invoke(tx)
    }

    /** Bir kunning BARCHA yozuvlarini karzinaga ko'chiradi. */
    suspend fun byDate(userId: Long, date: java.time.LocalDate): Int {
        val day = date.toString()
        val list = txDao.getAllForUser(userId).filter { it.date.take(10) == day }
        list.forEach { invoke(it) }
        return list.size
    }

    /** Mijozning BUTUN tarixini karzinaga ko'chiradi. */
    suspend fun byClient(userId: Long, clientName: String): Int {
        val cn = clientName.lowercase()
        val list = txDao.getAllForUser(userId).filter { it.clientName.lowercase() == cn }
        list.forEach { invoke(it) }
        return list.size
    }

    companion object {
        private val ZONE = ZoneId.of("Asia/Tashkent")
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
