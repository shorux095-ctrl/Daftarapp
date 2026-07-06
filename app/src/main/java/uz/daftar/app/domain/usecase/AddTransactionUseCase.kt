package uz.daftar.app.domain.usecase

import uz.daftar.app.core.parser.ParsedEntry
import uz.daftar.app.data.db.dao.ClientPriceDao
import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.dao.YukNarxDao
import uz.daftar.app.data.db.DaftarDatabase
import androidx.room.withTransaction
import uz.daftar.app.data.db.entity.ClientPriceEntity
import uz.daftar.app.data.db.entity.PriceHistoryEntity
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.data.db.entity.YukNarxEntity
import uz.daftar.app.domain.model.TxType
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Parser natijasini DB ga saqlash.
 * Bot.py'dagi yozuv saqlash mantiqi bilan bir xil:
 *   1) Har item uchun transactions ga yozuv
 *   2) Client prices (n) — price_history + client_prices
 *   3) T prices (t) — yuk_narx (price_group='t')
 *   4) T one-time (t?) — yuk_narx (price_group='t', one_time=1)
 *   Q (qarz qoldig'i) — t_override bilan saqlanadi
 */
class AddTransactionUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val priceHistoryDao: PriceHistoryDao,
    private val clientPriceDao: ClientPriceDao,
    private val yukNarxDao: YukNarxDao,
    private val db: DaftarDatabase
) {
    /** Saqlash natijasi: nechta yozuv, nechta narx */
    data class Result(
        val txCount: Int,
        val nPriceCount: Int,
        val tPriceCount: Int,
        val tOneTimeCount: Int
    )

    /**
     * Bir nechta yozuvni ATOMIK saqlaydi (@Transaction / withTransaction).
     * Telefon o'chsa yoki xato bo'lsa — YARIM saqlanmaydi: yo HAMMASI, yo HECH BIRI.
     * Shu tufayli baza hech qachon buzilmaydi.
     */
    suspend fun invokeAll(userId: Long, entries: List<ParsedEntry>): List<Result> =
        db.withTransaction {
            entries.map { invoke(userId, it) }
        }

    suspend operator fun invoke(userId: Long, entry: ParsedEntry): Result {
        // SANA MUAMMOSI TUZATILDI: aniq sana kiritilsa (03.07) — o'sha kunning SOAT 12:00 (tush)
        // vaqti bilan saqlanadi. Shunda tun/timezone siljishi sanani keyingi kunga o'tkazib yubormaydi.
        // Sana kiritilmasa — hozirgi to'liq vaqt (bugungi yozuv).
        val today = java.time.LocalDate.now(ZONE)
        val now = when {
            entry.date == null -> LocalDateTime.now(ZONE)
            entry.date == today -> LocalDateTime.now(ZONE)   // bugun — real vaqt
            else -> entry.date.atTime(12, 0)                 // o'tgan/kelasi kun — tush payti (barqaror)
        }
        val dateStr = now.format(ISO)
        val cn = entry.clientName.lowercase()

        // 1) Tranzaksiyalarni yozish
        var txCount = 0
        for ((type, amount) in entry.items) {
            val override = entry.tOneTime[type]  // t? a20 → t_override
            val tier = if (type in entry.t1Types) "t1" else null  // t1a → A yuki T1 tarifda
            txDao.insert(
                TransactionEntity(
                    userId = userId,
                    clientName = cn,
                    type = type.code,
                    amount = amount,
                    date = dateStr,
                    tOverride = override,
                    costTier = tier
                )
            )
            txCount++
        }

        // 2) Client narx (n) — price_history + client_prices
        var nCount = 0
        if (entry.clientPrices.isNotEmpty()) {
            for ((type, price) in entry.clientPrices) {
                priceHistoryDao.insert(
                    PriceHistoryEntity(
                        userId = userId,
                        clientName = cn,
                        priceType = type.code,
                        price = price,
                        date = dateStr
                    )
                )
                nCount++
            }
            // client_prices ni yangilash (oxirgi narx)
            val existing = clientPriceDao.get(userId, cn)
            val updated = (existing ?: ClientPriceEntity(userId = userId, clientName = cn))
                .copy(
                    aPrice = entry.clientPrices[TxType.A] ?: existing?.aPrice,
                    bPrice = entry.clientPrices[TxType.B] ?: existing?.bPrice,
                    cPrice = entry.clientPrices[TxType.C] ?: existing?.cPrice,
                    dPrice = entry.clientPrices[TxType.D] ?: existing?.dPrice,
                    kPrice = entry.clientPrices[TxType.K] ?: existing?.kPrice,
                    pPrice = entry.clientPrices[TxType.P] ?: existing?.pPrice,
                    qPrice = entry.clientPrices[TxType.Q] ?: existing?.qPrice
                )
            clientPriceDao.upsert(updated)
        }

        // 3) T narx (global) — yuk_narx
        var tCount = 0
        for ((type, price) in entry.tPrices) {
            yukNarxDao.insert(
                YukNarxEntity(
                    userId = userId,
                    clientName = null,
                    type = type.code,
                    price = price,
                    date = dateStr,
                    oneTime = 0,
                    priceGroup = "t"
                )
            )
            tCount++
        }

        // 4) T one-time (t?) — alohida belgi bilan
        var tOneCount = 0
        for ((type, price) in entry.tOneTime) {
            yukNarxDao.insert(
                YukNarxEntity(
                    userId = userId,
                    clientName = cn,
                    type = type.code,
                    price = price,
                    date = dateStr,
                    oneTime = 1,
                    priceGroup = "t"
                )
            )
            tOneCount++
        }

        return Result(txCount, nCount, tCount, tOneCount)
    }

    companion object {
        private val ZONE = ZoneId.of("Asia/Tashkent")
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
