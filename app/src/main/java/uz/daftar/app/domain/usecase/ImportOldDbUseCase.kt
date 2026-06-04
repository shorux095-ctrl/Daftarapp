package uz.daftar.app.domain.usecase

import android.database.sqlite.SQLiteDatabase
import uz.daftar.app.data.db.DaftarDatabase
import uz.daftar.app.data.db.dao.AliasDao
import uz.daftar.app.data.db.dao.ClientPriceDao
import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.RasxodDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.dao.YukNarxDao
import uz.daftar.app.data.db.entity.AliasEntity
import uz.daftar.app.data.db.entity.ClientPriceEntity
import uz.daftar.app.data.db.entity.PriceHistoryEntity
import uz.daftar.app.data.db.entity.RasxodEntity
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.data.db.entity.YukNarxEntity
import javax.inject.Inject

/**
 * Eski bot (qarz_bot ... .db) SQLite faylini o'qib, ilova bazasiga ko'chiradi.
 * AVVAL hamma ma'lumotni xotiraga o'qib oladi (tashqi .db ni yopadi),
 * KEYIN ilova bazasiga yozadi — shunda narxlar yo'qolmaydi.
 */
class ImportOldDbUseCase @Inject constructor(
    private val db: DaftarDatabase,
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao,
    private val clientPriceDao: ClientPriceDao,
    private val yukNarxDao: YukNarxDao,
    private val rasxodDao: RasxodDao,
    private val aliasDao: AliasDao
) {
    data class Result(
        val tx: Int = 0, val price: Int = 0, val clientPrice: Int = 0,
        val yukNarx: Int = 0, val rasxod: Int = 0, val alias: Int = 0, val ok: Boolean = true
    )

    private fun norm(s: String?): String = (s ?: "").replace("T", " ")

    suspend operator fun invoke(userId: Long, dbPath: String): Result {
        val src = runCatching {
            SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull() ?: return Result(ok = false)

        // ===== 1-bosqich: HAMMASINI xotiraga o'qib olamiz =====
        val txList = mutableListOf<TransactionEntity>()
        val prList = mutableListOf<PriceHistoryEntity>()
        val cpList = mutableListOf<ClientPriceEntity>()
        val ynList = mutableListOf<YukNarxEntity>()
        val rList = mutableListOf<RasxodEntity>()
        val aList = mutableListOf<AliasEntity>()

        try {
            runCatching {
                src.rawQuery("SELECT client_name,type,amount,date,t_override FROM transactions", null).use { c ->
                    while (c.moveToNext()) {
                        val cn = c.getString(0) ?: continue
                        txList.add(TransactionEntity(
                            userId = userId, clientName = cn,
                            type = (c.getString(1) ?: "").lowercase(),
                            amount = c.getDouble(2), date = norm(c.getString(3)),
                            tOverride = if (c.isNull(4)) null else c.getDouble(4)))
                    }
                }
            }
            runCatching {
                src.rawQuery("SELECT client_name,price_type,price,date FROM price_history", null).use { c ->
                    while (c.moveToNext()) {
                        prList.add(PriceHistoryEntity(
                            userId = userId, clientName = c.getString(0) ?: "",
                            priceType = (c.getString(1) ?: "").lowercase(),
                            price = c.getDouble(2), date = norm(c.getString(3))))
                    }
                }
            }
            runCatching {
                src.rawQuery("SELECT client_name,a_price,b_price,c_price,d_price,k_price,p_price,q_price FROM client_prices", null).use { c ->
                    fun d(i: Int): Double? = if (c.isNull(i)) null else c.getDouble(i)
                    while (c.moveToNext()) {
                        cpList.add(ClientPriceEntity(
                            userId = userId, clientName = c.getString(0) ?: "",
                            aPrice = d(1), bPrice = d(2), cPrice = d(3),
                            dPrice = d(4), kPrice = d(5), pPrice = d(6), qPrice = d(7)))
                    }
                }
            }
            runCatching {
                src.rawQuery("SELECT type,price,date,client_name,one_time,price_group FROM yuk_narx", null).use { c ->
                    while (c.moveToNext()) {
                        ynList.add(YukNarxEntity(
                            userId = userId,
                            clientName = if (c.isNull(3)) null else c.getString(3),
                            type = (c.getString(0) ?: "").lowercase(),
                            price = c.getDouble(1), date = norm(c.getString(2)),
                            oneTime = c.getInt(4),
                            priceGroup = c.getString(5) ?: "t"))
                    }
                }
            }
            runCatching {
                src.rawQuery("SELECT amount,note,date FROM rasxod", null).use { c ->
                    while (c.moveToNext()) {
                        rList.add(RasxodEntity(
                            userId = userId, amount = c.getDouble(0),
                            note = c.getString(1) ?: "", date = norm(c.getString(2))))
                    }
                }
            }
            runCatching {
                src.rawQuery("SELECT alias,canon FROM aliases", null).use { c ->
                    while (c.moveToNext()) {
                        aList.add(AliasEntity(userId = userId, alias = c.getString(0) ?: "", canon = c.getString(1) ?: ""))
                    }
                }
            }
        } finally {
            runCatching { src.close() }
        }

        // ===== 2-bosqich: eskini tozalab, yangisini yozamiz =====
        runCatching { txDao.clearAll(userId) }
        runCatching { priceDao.clearAll(userId) }
        runCatching { clientPriceDao.clearAll(userId) }
        runCatching { yukNarxDao.clearAll(userId) }
        runCatching { rasxodDao.clearAll(userId) }
        runCatching { aliasDao.clearAll(userId) }

        runCatching { if (txList.isNotEmpty()) txDao.insertAll(txList) }
        var nPr = 0; for (e in prList) runCatching { priceDao.insert(e); nPr++ }
        var nCp = 0; for (e in cpList) runCatching { clientPriceDao.upsert(e); nCp++ }
        var nYn = 0; for (e in ynList) runCatching { yukNarxDao.insert(e); nYn++ }
        var nR = 0; for (e in rList) runCatching { rasxodDao.insert(e); nR++ }
        var nA = 0; for (e in aList) runCatching { aliasDao.upsert(e); nA++ }

        return Result(txList.size, nPr, nCp, nYn, nR, nA, true)
    }
}
