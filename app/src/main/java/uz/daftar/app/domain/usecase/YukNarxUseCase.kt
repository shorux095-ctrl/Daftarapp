package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.YukNarxDao
import uz.daftar.app.data.db.entity.YukNarxEntity
import uz.daftar.app.domain.model.TxType
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Yuk turlari uchun global T narx qo'yish.
 * Bot.py'dagi "T a25 b30" buyrug'iga ekvivalent.
 */
class SetYukNarxUseCase @Inject constructor(
    private val yukNarxDao: YukNarxDao
) {
    suspend operator fun invoke(userId: Long, prices: Map<TxType, Double>) {
        val now = LocalDateTime.now(ZONE).format(ISO)
        for ((type, price) in prices) {
            if (type.code !in setOf("a", "b", "c", "d", "k")) continue
            yukNarxDao.insert(
                YukNarxEntity(
                    userId = userId,
                    clientName = null,  // global = client_name NULL
                    type = type.code,
                    price = price,
                    date = now,
                    oneTime = 0,
                    priceGroup = "t"
                )
            )
        }
    }

    companion object {
        private val ZONE = ZoneId.of("Asia/Tashkent")
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

/**
 * Hozirgi T narxlarni o'qish — har yuk turi uchun oxirgi global narx.
 */
class GetCurrentYukNarxUseCase @Inject constructor(
    private val yukNarxDao: YukNarxDao
) {
    suspend operator fun invoke(userId: Long): Map<TxType, Double?> {
        val result = mutableMapOf<TxType, Double?>()
        for (code in listOf("a", "b", "c", "d", "k")) {
            val type = TxType.fromCode(code) ?: continue
            val entry = yukNarxDao.getLatestGlobal(userId, code, "t")
            result[type] = entry?.price
        }
        return result
    }
}
