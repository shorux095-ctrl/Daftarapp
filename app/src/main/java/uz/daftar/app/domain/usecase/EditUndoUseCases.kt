package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.domain.model.TxType
import javax.inject.Inject

/** Oxirgi saqlangan yozuvni (bitta save guruhini) bekor qiladi. */
class UndoLastUseCase @Inject constructor(
    private val txDao: TransactionDao
) {
    /** @return bekor qilingan mijoz nomi yoki null (yozuv yo'q) */
    suspend operator fun invoke(userId: Long): String? {
        val last = txDao.getLast(userId) ?: return null
        txDao.deleteSave(userId, last.clientName, last.date)
        return last.clientName
    }
}

/** "edit Ali a10 a15" — mijozning eng oxirgi (type+amount) yozuvini yangi qiymatga o'zgartiradi. */
class EditByMatchUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val editTx: EditTransactionUseCase
) {
    data class Result(val ok: Boolean, val message: String)

    suspend operator fun invoke(
        userId: Long,
        clientName: String,
        oldType: TxType,
        oldAmount: Double,
        newType: TxType,
        newAmount: Double
    ): Result {
        val txs = txDao.getByClient(userId, clientName.lowercase())
        // type + amount mos keladigan eng oxirgi yozuv
        val match = txs.filter { it.type == oldType.code && it.amount == oldAmount }
            .maxByOrNull { it.date }
            ?: return Result(false, "Topilmadi: ${clientName} ${oldType.code.uppercase()}${oldAmount}")
        editTx(
            id = match.id,
            userId = userId,
            clientName = clientName,
            type = newType,
            amount = newAmount,
            date = match.date,
            tOverride = match.tOverride
        )
        return Result(true, "✅ ${clientName.replaceFirstChar { it.uppercase() }}: ${oldType.code.uppercase()}${oldAmount.toLong()} → ${newType.code.uppercase()}${newAmount.toLong()}")
    }
}
