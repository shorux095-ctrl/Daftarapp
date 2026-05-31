package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.YukNarxDao
import uz.daftar.app.data.db.entity.YukNarxEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** "T a10 b20" yoki "T1 a16.5 b1.9" — mijozsiz global tannarx (T yoki T1) qo'yadi. */
class SetGlobalPriceUseCase @Inject constructor(
    private val yukDao: YukNarxDao
) {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** group = "t" (asosiy) yoki "t1" (2-daraja) */
    suspend operator fun invoke(
        userId: Long,
        group: String,
        prices: Map<String, Double>,
        date: LocalDate? = null
    ) {
        val dateStr = (date?.atTime(12, 0) ?: LocalDateTime.now()).format(ISO)
        for ((type, price) in prices) {
            yukDao.insert(
                YukNarxEntity(
                    userId = userId,
                    clientName = null,
                    type = type,
                    price = price,
                    date = dateStr,
                    oneTime = 0,
                    priceGroup = group
                )
            )
        }
    }
}
