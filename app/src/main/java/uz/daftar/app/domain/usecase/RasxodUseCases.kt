package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.RasxodDao
import uz.daftar.app.data.db.entity.RasxodEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Rasxod (xarajat) — yoqilg'i, ovqat, ta'mir va h.k.
 * Bot.py'dagi rasxod jadval bilan bir xil.
 */
class AddRasxodUseCase @Inject constructor(
    private val rasxodDao: RasxodDao
) {
    suspend operator fun invoke(userId: Long, amount: Double, note: String, date: LocalDateTime = LocalDateTime.now(ZONE)) {
        rasxodDao.insert(
            RasxodEntity(
                userId = userId,
                amount = amount,
                note = note,
                date = date.format(ISO)
            )
        )
    }

    companion object {
        internal val ZONE: ZoneId = ZoneId.of("Asia/Tashkent")
        internal val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

class DeleteRasxodUseCase @Inject constructor(
    private val rasxodDao: RasxodDao
) {
    suspend operator fun invoke(id: Long): Boolean =
        rasxodDao.deleteById(id) > 0
}

/** v159: rasxod summa/izohini tahrirlash */
class UpdateRasxodUseCase @Inject constructor(
    private val rasxodDao: RasxodDao
) {
    suspend operator fun invoke(id: Long, amount: Double, note: String): Boolean =
        rasxodDao.updateById(id, amount, note.trim()) > 0
}

class GetRasxodRangeUseCase @Inject constructor(
    private val rasxodDao: RasxodDao
) {
    suspend operator fun invoke(userId: Long, from: LocalDate, toInclusive: LocalDate): List<RasxodEntity> {
        val s = from.atStartOfDay().format(AddRasxodUseCase.ISO)
        val e = toInclusive.plusDays(1).atStartOfDay().format(AddRasxodUseCase.ISO)
        return rasxodDao.getRange(userId, s, e)
    }
}

class GetRasxodTotalUseCase @Inject constructor(
    private val rasxodDao: RasxodDao
) {
    suspend operator fun invoke(userId: Long, from: LocalDate, toInclusive: LocalDate): Long {
        val s = from.atStartOfDay().format(AddRasxodUseCase.ISO)
        val e = toInclusive.plusDays(1).atStartOfDay().format(AddRasxodUseCase.ISO)
        return (rasxodDao.getTotalRange(userId, s, e) ?: 0.0).roundToLong()
    }
}
