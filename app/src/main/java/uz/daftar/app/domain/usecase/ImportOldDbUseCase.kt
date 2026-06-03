package uz.daftar.app.domain.usecase

import android.database.sqlite.SQLiteDatabase
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
 * Har bir jadval alohida himoyalangan — biri xato bo'lsa, qolganlari ko'chadi.
 */
class ImportOldDbUseCase @Inject constructor(
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
        var nTx = 0; var nPr = 0; var nCp = 0; var nYn = 0; var nR = 0; var nA = 0
        val db = runCatching {
            SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull() ?: return Result(ok = false)

        try {
            // transactions
            runCatching {
                val list = mutableListOf<TransactionEntity>()
                db.rawQuery("SELECT client_name,type,amount,date,t_override FROM transactions", null).use { c ->
                    while (c.moveToNext()) {
                        val cn = c.getString(0) ?: continue
                        list.add(
                            TransactionEntity(
                                userId = userId, clientName = cn,
                                type = (c.getString(1) ?: "").lowercase(),
                                amount = c.getDouble(2), date = norm(c.getString(3)),
                                tOverride = if (c.isNull(4)) null else c.getDouble(4)
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) { txDao.insertAll(list); nTx = list.size }
            }
            // price_history
            runCatching {
                db.rawQuery("SELECT client_name,price_type,price,date FROM price_history", null).use { c ->
                    while (c.moveToNext()) {
                        priceDao.insert(
                            PriceHistoryEntity(
                                userId = userId, clientName = c.getString(0) ?: "",
                                priceType = (c.getString(1) ?: "").lowercase(),
                                price = c.getDouble(2), date = norm(c.getString(3))
                            )
                        ); nPr++
                    }
                }
            }
            // client_prices
            runCatching {
                db.rawQuery("SELECT client_name,a_price,b_price,c_price,d_price,k_price,p_price,q_price FROM client_prices", null).use { c ->
                    fun d(i: Int): Double? = if (c.isNull(i)) null else c.getDouble(i)
                    while (c.moveToNext()) {
                        clientPriceDao.upsert(
                            ClientPriceEntity(
                                userId = userId, clientName = c.getString(0) ?: "",
                                aPrice = d(1), bPrice = d(2), cPrice = d(3),
                                dPrice = d(4), kPrice = d(5), pPrice = d(6), qPrice = d(7)
                            )
                        ); nCp++
                    }
                }
            }
            // yuk_narx
            runCatching {
                db.rawQuery("SELECT type,price,date,client_name,one_time,price_group FROM yuk_narx", null).use { c ->
                    while (c.moveToNext()) {
                        yukNarxDao.insert(
                            YukNarxEntity(
                                userId = userId,
                                clientName = if (c.isNull(3)) null else c.getString(3),
                                type = (c.getString(0) ?: "").lowercase(),
                                price = c.getDouble(1), date = norm(c.getString(2)),
                                oneTime = c.getInt(4),
                                priceGroup = c.getString(5) ?: "t"
                            )
                        ); nYn++
                    }
                }
            }
            // rasxod
            runCatching {
                db.rawQuery("SELECT amount,note,date FROM rasxod", null).use { c ->
                    while (c.moveToNext()) {
                        rasxodDao.insert(
                            RasxodEntity(
                                userId = userId, amount = c.getDouble(0),
                                note = c.getString(1) ?: "", date = norm(c.getString(2))
                            )
                        ); nR++
                    }
                }
            }
            // aliases
            runCatching {
                db.rawQuery("SELECT alias,canon FROM aliases", null).use { c ->
                    while (c.moveToNext()) {
                        aliasDao.upsert(
                            AliasEntity(userId = userId, alias = c.getString(0) ?: "", canon = c.getString(1) ?: "")
                        ); nA++
                    }
                }
            }
        } finally {
            runCatching { db.close() }
        }
        return Result(nTx, nPr, nCp, nYn, nR, nA, true)
    }
}
