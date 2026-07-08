package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import javax.inject.Inject

/**
 * Bitta tranzaksiyani edit qilish — miqdor va turini yangilash.
 * Bot.py'dagi "edit" handleriga ekvivalent.
 */
class EditTransactionUseCase @Inject constructor(
    private val txDao: TransactionDao
) {
    suspend operator fun invoke(
        id: Long,
        userId: Long,
        clientName: String,
        type: TxType,
        amount: Double,
        date: String,
        tOverride: Double? = null,
        costTier: String? = null,
        note: String? = null
    ) {
        txDao.insert(
            TransactionEntity(
                id = id,
                userId = userId,
                clientName = clientName.lowercase(),
                type = type.code,
                amount = amount,
                date = date,
                tOverride = tOverride,
                costTier = costTier,
                note = note
            )
        )
    }
}

/**
 * Tranzaksiyani o'chirish — karzinaga ko'chiriladi (7 kun saqlanadi).
 * Bot.py'dagi "x" handleriga ekvivalent.
 */
// v152: DeleteTransactionUseCase OLIB TASHLANDI — karzinani chetlab o'tardi.
// O'chirish uchun faqat DeleteToKarzinaUseCase ishlatilsin.
