package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.ClientLimitDao
import uz.daftar.app.data.db.dao.ClientReminderDao
import uz.daftar.app.data.db.entity.ClientLimitEntity
import uz.daftar.app.data.db.entity.ClientReminderEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Eslatma — mijoz qarzini necha kundan keyin eslatish (bot.py'dagi eslatma).
 * Limit — mijoz qarzi shu summadan oshsa ogohlantirish (bot.py'dagi limit).
 *
 * Eslatma va limit alohida jadvallarda (bot-8.py'da ajratilgan).
 */

// ─────────── Eslatma ───────────

class GetRemindersUseCase @Inject constructor(
    private val dao: ClientReminderDao
) {
    suspend operator fun invoke(userId: Long): List<ClientReminderEntity> = dao.getActive(userId)
}

class SetReminderUseCase @Inject constructor(
    private val dao: ClientReminderDao
) {
    suspend operator fun invoke(userId: Long, clientName: String, days: Int) {
        dao.upsert(
            ClientReminderEntity(
                userId = userId,
                clientName = clientName.lowercase(),
                days = days
            )
        )
    }
}

class DeleteReminderUseCase @Inject constructor(
    private val dao: ClientReminderDao
) {
    suspend operator fun invoke(userId: Long, clientName: String) =
        dao.delete(userId, clientName.lowercase())
}

// ─────────── Limit ───────────

class GetLimitsUseCase @Inject constructor(
    private val dao: ClientLimitDao
) {
    suspend operator fun invoke(userId: Long): List<ClientLimitEntity> = dao.getAll(userId)
}

class SetLimitUseCase @Inject constructor(
    private val dao: ClientLimitDao
) {
    suspend operator fun invoke(userId: Long, clientName: String, limitAmount: Double) {
        dao.upsert(
            ClientLimitEntity(
                userId = userId,
                clientName = clientName.lowercase(),
                limitAmt = limitAmount,
                created = LocalDateTime.now(ZoneId.of("Asia/Tashkent"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            )
        )
    }
}

class DeleteLimitUseCase @Inject constructor(
    private val dao: ClientLimitDao
) {
    suspend operator fun invoke(userId: Long, clientName: String) =
        dao.delete(userId, clientName.lowercase())
}
