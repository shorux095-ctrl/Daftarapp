package uz.daftar.app.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uz.daftar.app.data.db.entity.TransactionEntity

/** SUM(amount) — yuk turi bo'yicha (SQL yig'indisi, 100k+ uchun tez) */
data class CargoTypeSum(val type: String, val total: Double)

/** (type, amount, date) — Sklad qoldig'ini SANA bo'yicha ayirish uchun (oldindan yig'ilmaydi) */
data class CargoTxLite(val type: String, val amount: Double, val date: String)

@Dao
interface TransactionDao {

    /** v184: har mijozning ENG BIRINCHI yozuv sanasi (yangi klientlarni aniqlash uchun) */
    @Query("SELECT LOWER(client_name) AS name, MIN(date) AS first FROM transactions WHERE user_id = :userId GROUP BY LOWER(client_name)")
    suspend fun getClientFirstDates(userId: Long): List<ClientFirst>


    // ───────── Paging 3 (Qidiruv) ─────────
    @Query("""
        SELECT * FROM transactions
        WHERE user_id = :userId AND LOWER(client_name) LIKE '%' || LOWER(:query) || '%'
        ORDER BY date DESC, id DESC
    """)
    fun pagingByClientLike(userId: Long, query: String): PagingSource<Int, TransactionEntity>

    @Query("""
        SELECT * FROM transactions
        WHERE user_id = :userId AND date >= :start AND date < :end
        ORDER BY date DESC, id DESC
    """)
    fun pagingByRange(userId: Long, start: String, end: String): PagingSource<Int, TransactionEntity>

    /** 🏆 Statistika: berilgan davrda eng ko'p yuk (A/B/C/D/K) olgan mijoz */
    @Query("""
        SELECT client_name AS clientName, SUM(amount) AS total
        FROM transactions
        WHERE user_id = :userId AND LOWER(type) IN ('a','b','c','d','k') AND date BETWEEN :start AND :end
        GROUP BY client_name ORDER BY total DESC LIMIT 1
    """)
    suspend fun topCargoClient(userId: Long, start: String, end: String): ClientTotal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(txs: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE user_id = :userId")
    suspend fun clearAll(userId: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY id DESC LIMIT 1")
    suspend fun getLast(userId: Long): TransactionEntity?

    @Deprecated("v152: XAVFLI — retro yozuvlar (12:00:00) bir sanada to'planadi, begona yozuv o'chishi mumkin. UndoLast endi ID bo'yicha karzina orqali ishlaydi.")
    @Query("DELETE FROM transactions WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName) AND date = :date")
    suspend fun deleteSave(userId: Long, clientName: String, date: String): Int

    /** v152: oxirgi saqlash guruhi chegarasi — boshqa (client/date) yozuvlarning eng katta id'si */
    @Query("SELECT MAX(id) FROM transactions WHERE user_id = :userId AND NOT (LOWER(client_name) = LOWER(:clientName) AND date = :date)")
    suspend fun maxIdExcept(userId: Long, clientName: String, date: String): Long?

    /** v152: faqat oxirgi saqlashda kiritilgan yozuvlar (chegaradan keyingilari) */
    @Query("SELECT * FROM transactions WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName) AND date = :date AND id > :afterId")
    suspend fun getSaveGroupAfter(userId: Long, clientName: String, date: String, afterId: Long): List<uz.daftar.app.data.db.entity.TransactionEntity>

    @Query("DELETE FROM transactions WHERE user_id = :userId AND date >= :start AND date < :end")
    suspend fun deleteByDateRange(userId: Long, start: String, end: String): Int

    @Query("DELETE FROM transactions WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName)")
    suspend fun deleteByClient(userId: Long, clientName: String): Int

    @Query("UPDATE transactions SET cost_tier = :tier WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName) AND date >= :start AND date < :end")
    suspend fun setTierAll(userId: Long, clientName: String, start: String, end: String, tier: String?): Int

    @Query("UPDATE transactions SET cost_tier = :tier WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName) AND type = :type AND date >= :start AND date < :end")
    suspend fun setTierType(userId: Long, clientName: String, type: String, start: String, end: String, tier: String?): Int

    @Query("UPDATE transactions SET cost_tier = :tier WHERE id = :id")
    suspend fun setTierById(id: Long, tier: String?): Int

    /** Sana oralig'idagi barcha yozuvlar — kunlik/oylik hisobotlar uchun. */
    @Query("""
        SELECT * FROM transactions
        WHERE user_id = :userId AND date >= :start AND date < :end
        ORDER BY date
    """)
    fun observeRange(userId: Long, start: String, end: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE user_id = :userId AND date >= :start AND date < :end
        ORDER BY date
    """)
    suspend fun getRange(userId: Long, start: String, end: String): List<TransactionEntity>

    /** Bitta mijozning barcha yozuvlari — qarz hisoblash uchun. */
    @Query("""
        SELECT * FROM transactions
        WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName)
        ORDER BY date
    """)
    suspend fun getByClient(userId: Long, clientName: String): List<TransactionEntity>

    /** Mijoz oxirgi to'lov sanasi. */
    @Query("""
        SELECT MAX(date) FROM transactions
        WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName) AND type = 'p'
    """)
    suspend fun getLastPaymentDate(userId: Long, clientName: String): String?

    /** Mijoz oxirgi yuk sanasi (a/b/c/d/k/q). */
    @Query("""
        SELECT MAX(date) FROM transactions
        WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName)
          AND type IN ('a','b','c','d','k','q')
    """)
    suspend fun getLastYukDate(userId: Long, clientName: String): String?

    /** Barcha mijoz ismlari (unique). */
    @Query("SELECT DISTINCT LOWER(client_name) FROM transactions WHERE user_id = :userId ORDER BY client_name")
    suspend fun getAllClientNames(userId: Long): List<String>

    @Query("SELECT * FROM transactions WHERE user_id = :userId")
    suspend fun getAllForUser(userId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT DISTINCT LOWER(client_name) FROM transactions WHERE user_id = :userId ORDER BY client_name")
    fun observeAllClientNames(userId: Long): Flow<List<String>>

    /**
     * Mijoz ismi prefiksi bo'yicha takliflar — autocomplete uchun.
     * Eng yangi yozuv qilingan mijozlar avval chiqadi (recency-based ranking).
     */
    @Query("""
        SELECT LOWER(client_name) AS name
        FROM transactions
        WHERE user_id = :userId AND (
            LOWER(client_name) LIKE LOWER(:prefix) || '%'
            OR LOWER(client_name) LIKE '%' || LOWER(:prefix) || '%'
        )
        GROUP BY LOWER(client_name)
        ORDER BY
            CASE WHEN LOWER(client_name) LIKE LOWER(:prefix) || '%' THEN 0 ELSE 1 END,
            MAX(date) DESC
        LIMIT 8
    """)
    suspend fun suggestClients(userId: Long, prefix: String): List<String>

    /** Ism o'zgartirish (rename). */
    @Query("""
        UPDATE transactions SET client_name = :newName
        WHERE user_id = :userId AND LOWER(client_name) = LOWER(:oldName)
    """)
    suspend fun renameClient(userId: Long, oldName: String, newName: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName)")
    suspend fun countByClient(userId: Long, clientName: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE user_id = :userId")
    suspend fun countAll(userId: Long): Int

    // Yuk turlari (A/B/C/D/K) bo'yicha sotilgan jami — bazada hisoblanadi (barcha qatorni yuklamaydi)
    @Query("SELECT type AS type, SUM(amount) AS total FROM transactions WHERE user_id = :userId AND type IN ('a','b','c','d','k') GROUP BY type")
    suspend fun sumByCargoType(userId: Long): List<CargoTypeSum>

    // Reaktiv (Flow) — tranzaksiya o'zgarsa avtomatik yangilanadi (Sklad qoldig'i uchun)
    @Query("SELECT type AS type, SUM(amount) AS total FROM transactions WHERE user_id = :userId AND type IN ('a','b','c','d','k') GROUP BY type")
    fun observeSumByCargoType(userId: Long): Flow<List<CargoTypeSum>>

    // Reaktiv — yuk turi sotuvlari SANA bilan (Sklad: qo'shilgan kundan beri ayirish uchun)
    @Query("SELECT type AS type, amount AS amount, date AS date FROM transactions WHERE user_id = :userId AND type IN ('a','b','c','d','k')")
    fun observeCargoTxLite(userId: Long): Flow<List<CargoTxLite>>
}

/** 🏆 Statistika natijasi: mijoz + jami miqdor */
data class ClientTotal(val clientName: String, val total: Double)

/** v184: TOP ekrani uchun — mijoz + birinchi yozuv sanasi */
data class ClientFirst(val name: String, val first: String)
