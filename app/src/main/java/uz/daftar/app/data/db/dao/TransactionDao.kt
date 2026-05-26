package uz.daftar.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uz.daftar.app.data.db.entity.TransactionEntity

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(txs: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName)")
    suspend fun deleteByClient(userId: Long, clientName: String): Int

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

    @Query("SELECT DISTINCT LOWER(client_name) FROM transactions WHERE user_id = :userId ORDER BY client_name")
    fun observeAllClientNames(userId: Long): Flow<List<String>>

    /**
     * Mijoz ismi prefiksi bo'yicha takliflar — autocomplete uchun.
     * Eng yangi yozuv qilingan mijozlar avval chiqadi (recency-based ranking).
     */
    @Query("""
        SELECT LOWER(client_name) AS name
        FROM transactions
        WHERE user_id = :userId AND LOWER(client_name) LIKE LOWER(:prefix) || '%'
        GROUP BY LOWER(client_name)
        ORDER BY MAX(date) DESC
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
}
