package uz.daftar.app.domain.usecase

import uz.daftar.app.core.parser.DaftarParser
import uz.daftar.app.data.db.dao.AliasDao
import uz.daftar.app.data.db.dao.ClientPriceDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.AliasEntity
import javax.inject.Inject

/**
 * Mijoz nomini boshqa nomga merge qilish (alias).
 * Bot.py'dagi `alias ziya.ziyafet` → "ziya"'ning hamma yozuvlari "ziyafet"ga o'tadi.
 */
class AddAliasUseCase @Inject constructor(
    private val aliasDao: AliasDao,
    private val txDao: TransactionDao,
    private val priceDao: uz.daftar.app.data.db.dao.PriceHistoryDao,
    private val clientPriceDao: ClientPriceDao
) {
    suspend operator fun invoke(userId: Long, aliasName: String, canonName: String): Result {
        val alias = DaftarParser.normalizeName(aliasName)
        val canon = DaftarParser.normalizeName(canonName)
        if (alias == canon || alias.isBlank() || canon.isBlank()) {
            return Result.Failure("Ikkalasi bir xil yoki bo'sh")
        }

        // Alias saqlash
        aliasDao.upsert(AliasEntity(userId = userId, alias = alias, canon = canon))
        // Eski nom yozuvlarini yangi nomga ko'chirish
        val moved = txDao.renameClient(userId, alias, canon)
        // v152: NARX jadvallari ham ko'chiriladi (aks holda qarz noto'g'ri kamayib ko'rinardi)
        runCatching { priceDao.renameClient(userId, alias, canon) }
        runCatching {
            clientPriceDao.renameClient(userId, alias, canon)
            clientPriceDao.deleteLeftover(userId, alias)
        }
        return Result.Success(moved)
    }

    sealed class Result {
        data class Success(val movedCount: Int) : Result()
        data class Failure(val reason: String) : Result()
    }
}

/**
 * Mijoz nomini qayta nomlash (rename). Alias'dan farqi — alias jadval'ga yozmaydi.
 */
class RenameClientUseCase @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: uz.daftar.app.data.db.dao.PriceHistoryDao,
    private val clientPriceDao: ClientPriceDao
) {
    suspend operator fun invoke(userId: Long, oldName: String, newName: String): Int {
        val oldN = DaftarParser.normalizeName(oldName)
        val newN = DaftarParser.normalizeName(newName)
        if (oldN == newN || oldN.isBlank() || newN.isBlank()) return 0
        val moved = txDao.renameClient(userId, oldN, newN)
        // v152: narx tarixi va mijoz narxlari ham yangi nomga o'tadi
        runCatching { priceDao.renameClient(userId, oldN, newN) }
        runCatching {
            clientPriceDao.renameClient(userId, oldN, newN)
            clientPriceDao.deleteLeftover(userId, oldN)
        }
        return moved
    }
}

/** Aliaslarni ko'rish (sozlamalar ekrani uchun) */
class GetAliasesUseCase @Inject constructor(
    private val aliasDao: AliasDao
) {
    suspend operator fun invoke(userId: Long): List<AliasEntity> = aliasDao.getAll(userId)
}

class DeleteAliasUseCase @Inject constructor(
    private val aliasDao: AliasDao
) {
    suspend operator fun invoke(userId: Long, alias: String) {
        aliasDao.delete(userId, DaftarParser.normalizeName(alias))
    }
}
