package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.TransactionDao
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Mavjud yozuvlarni T1 tarifga o'tkazadi (cost_tier = "t1").
 * type = null bo'lsa barcha yuk turlari, aks holda faqat o'sha tur.
 * Sana oralig'i [startDate .. endDate] (ikkalasi ham kiritiladi).
 */
class SetT1TierUseCase @Inject constructor(
    private val txDao: TransactionDao
) {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend operator fun invoke(
        userId: Long,
        clientName: String,
        type: String?,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        val start = startDate.atStartOfDay().format(ISO)
        val end = endDate.plusDays(1).atStartOfDay().format(ISO)
        return if (type == null) {
            txDao.setTierAll(userId, clientName, start, end, "t1")
        } else {
            txDao.setTierType(userId, clientName, type, start, end, "t1")
        }
    }
}
