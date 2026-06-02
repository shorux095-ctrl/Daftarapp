package uz.daftar.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.Transaction
import uz.daftar.app.domain.model.TxType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao
) {

    /** Bugungi yozuvlar (Flow — UI avtomatik yangilanadi) */
    fun observeToday(userId: Long): Flow<List<Transaction>> {
        val zone = ZoneId.of("Asia/Tashkent")
        val now = LocalDateTime.now(zone)
        val start = now.toLocalDate().atStartOfDay()
        val end = start.plusDays(1)
        return txDao.observeRange(userId, isoFormat(start), isoFormat(end)).map { rows ->
            rows.mapNotNull { it.toDomain() }
        }
    }

    /** Berilgan sana oraliq'i — Flow */
    fun observeBetween(userId: Long, startDate: LocalDate, endDateExclusive: LocalDate): Flow<List<Transaction>> {
        val start = startDate.atStartOfDay()
        val end = endDateExclusive.atStartOfDay()
        return txDao.observeRange(userId, isoFormat(start), isoFormat(end)).map { rows ->
            rows.mapNotNull { it.toDomain() }
        }
    }

    /** Bir nechta yozuvni id bo'yicha o'chirish (multi-delete uchun) */
    suspend fun deleteByIds(ids: Collection<Long>) {
        ids.forEach { txDao.deleteById(it) }
    }

    /** Mijoz nomi takliflar — autocomplete uchun */
    suspend fun suggestClients(userId: Long, prefix: String): List<String> {
        if (prefix.isBlank()) return emptyList()
        return txDao.suggestClients(userId, prefix.lowercase())
    }

    /** Berilgan sana oralig'idagi yozuvlar */
    suspend fun getRange(userId: Long, start: LocalDateTime, end: LocalDateTime): List<Transaction> {
        return txDao.getRange(userId, isoFormat(start), isoFormat(end)).mapNotNull { it.toDomain() }
    }

    /** Mijozning barcha yozuvlari */
    suspend fun getByClient(userId: Long, clientName: String): List<Transaction> {
        return txDao.getByClient(userId, clientName).mapNotNull { it.toDomain() }
    }

    suspend fun insert(userId: Long, clientName: String, type: TxType, amount: Double, date: LocalDateTime): Long {
        val entity = TransactionEntity(
            userId = userId,
            clientName = clientName.lowercase(),
            type = type.code,
            amount = amount,
            date = isoFormat(date)
        )
        return txDao.insert(entity)
    }

    suspend fun deleteByClient(userId: Long, clientName: String): Int {
        return txDao.deleteByClient(userId, clientName)
    }

    fun observeClientNames(userId: Long): Flow<List<String>> =
        txDao.observeAllClientNames(userId)

    companion object {
        private val ISO_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private fun isoFormat(dt: LocalDateTime): String = dt.format(ISO_FMT)
    }
}

internal fun TransactionEntity.toDomain(): Transaction? {
    val txType = TxType.fromCode(type) ?: return null
    val dt = parseDateTime(date) ?: return null
    return Transaction(
        id = id,
        clientName = clientName,
        type = txType,
        amount = amount,
        date = dt,
        tOverride = tOverride
    )
}

private fun parseDateTime(s: String): LocalDateTime? {
    return try {
        // Bot.py turli format saqlaydi — qabul qilamiz
        val cleaned = s.replace("T", " ").substring(0, minOf(s.length, 19))
        LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    } catch (e: Exception) {
        try {
            LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (e2: Exception) {
            null
        }
    }
}
