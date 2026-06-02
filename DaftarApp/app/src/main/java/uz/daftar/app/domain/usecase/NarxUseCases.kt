package uz.daftar.app.domain.usecase

import uz.daftar.app.data.db.dao.ClientPriceDao
import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.entity.ClientPriceEntity
import uz.daftar.app.data.db.entity.PriceHistoryEntity
import uz.daftar.app.domain.model.TxType
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * N narx — mijozga alohida narx (bot.py'dagi save_price ekvivalenti).
 *
 * Bot.py mantiqi:
 *  - price_history jadvalga sana bilan yoziladi (tarix saqlanadi)
 *  - client_prices jadvalga oxirgi narx yoziladi (tezkor o'qish uchun)
 *  - Qarz hisoblashda: N narx bo'lsa N, bo'lmasa global T narx ishlatiladi
 */
class GetClientNarxUseCase @Inject constructor(
    private val clientPriceDao: ClientPriceDao
) {
    /** Mijozning hozirgi N narxlari (type → narx). */
    suspend operator fun invoke(userId: Long, clientName: String): Map<TxType, Double?> {
        val cp = clientPriceDao.get(userId, clientName.lowercase()) ?: return emptyMap()
        return mapOf(
            TxType.A to cp.aPrice,
            TxType.B to cp.bPrice,
            TxType.C to cp.cPrice,
            TxType.D to cp.dPrice,
            TxType.K to cp.kPrice
        )
    }
}

class SetClientNarxUseCase @Inject constructor(
    private val clientPriceDao: ClientPriceDao,
    private val priceHistoryDao: PriceHistoryDao
) {
    /**
     * N narxlarni saqlash. input: type → narx (faqat o'zgartirilganlar).
     * Ham price_history (tarix), ham client_prices (oxirgi) ga yoziladi.
     */
    suspend operator fun invoke(userId: Long, clientName: String, prices: Map<TxType, Double>) {
        val cn = clientName.lowercase()
        val now = LocalDateTime.now(ZONE).format(ISO)

        // price_history — har bir narx uchun sana bilan
        for ((type, price) in prices) {
            priceHistoryDao.insert(
                PriceHistoryEntity(
                    userId = userId,
                    clientName = cn,
                    priceType = type.code,
                    price = price,
                    date = now
                )
            )
        }

        // client_prices — oxirgi qiymatlarni yangilash
        val existing = clientPriceDao.get(userId, cn)
        val updated = (existing ?: ClientPriceEntity(userId = userId, clientName = cn)).copy(
            aPrice = prices[TxType.A] ?: existing?.aPrice,
            bPrice = prices[TxType.B] ?: existing?.bPrice,
            cPrice = prices[TxType.C] ?: existing?.cPrice,
            dPrice = prices[TxType.D] ?: existing?.dPrice,
            kPrice = prices[TxType.K] ?: existing?.kPrice
        )
        clientPriceDao.upsert(updated)
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Tashkent")
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

/**
 * Mijozning hozirgi (eng oxirgi) narxlari price_history'dan — bubble'da [4.5] ko'rsatish uchun.
 * Qarz hisobi bilan bir xil manbadan (price_history) olinadi.
 */
class GetClientUnitPricesUseCase @Inject constructor(
    private val priceDao: PriceHistoryDao
) {
    suspend operator fun invoke(userId: Long, clientName: String): Map<TxType, Double> {
        val all = priceDao.getAllForClient(userId, clientName.lowercase())
        val result = mutableMapOf<TxType, Double>()
        for (p in all) {
            val type = TxType.fromCode(p.priceType) ?: continue
            result[type] = p.price  // ASC tartib — eng oxirgisi eng yangi narx
        }
        return result
    }
}
