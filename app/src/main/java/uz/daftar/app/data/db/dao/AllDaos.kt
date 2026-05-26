package uz.daftar.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uz.daftar.app.data.db.entity.AliasEntity
import uz.daftar.app.data.db.entity.ClientEntity
import uz.daftar.app.data.db.entity.ClientLimitEntity
import uz.daftar.app.data.db.entity.ClientPriceEntity
import uz.daftar.app.data.db.entity.ClientReminderEntity
import uz.daftar.app.data.db.entity.PriceHistoryEntity
import uz.daftar.app.data.db.entity.RasxodEntity
import uz.daftar.app.data.db.entity.YukNarxEntity

@Dao
interface PriceHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(price: PriceHistoryEntity): Long

    /** Berilgan sana yoki undan oldingi eng oxirgi narx. */
    @Query("""
        SELECT price FROM price_history
        WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName)
          AND price_type = :type AND date <= :at
        ORDER BY date DESC, id DESC LIMIT 1
    """)
    suspend fun getPriceAt(userId: Long, clientName: String, type: String, at: String): Double?

    /** Topilmasa — keyingi eng yaqin narx (retroaktiv yozuv). */
    @Query("""
        SELECT price FROM price_history
        WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName)
          AND price_type = :type AND date > :at
        ORDER BY date ASC LIMIT 1
    """)
    suspend fun getNextPrice(userId: Long, clientName: String, type: String, at: String): Double?

    /** Bitta mijoz uchun barcha narxlar — batch calculate_debt uchun. */
    @Query("""
        SELECT * FROM price_history
        WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName)
        ORDER BY price_type, date ASC, id ASC
    """)
    suspend fun getAllForClient(userId: Long, clientName: String): List<PriceHistoryEntity>
}

@Dao
interface ClientPriceDao {
    @Query("SELECT * FROM client_prices WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName)")
    suspend fun get(userId: Long, clientName: String): ClientPriceEntity?

    @Query("SELECT * FROM client_prices WHERE user_id = :userId")
    fun observeAll(userId: Long): Flow<List<ClientPriceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cp: ClientPriceEntity)
}

@Dao
interface YukNarxDao {
    @Query("""
        SELECT * FROM yuk_narx
        WHERE user_id = :userId AND client_name IS NULL
          AND type = :type AND price_group = :group AND one_time = 0
        ORDER BY date DESC LIMIT 1
    """)
    suspend fun getLatestGlobal(userId: Long, type: String, group: String): YukNarxEntity?

    @Query("""
        SELECT * FROM yuk_narx
        WHERE user_id = :userId AND LOWER(client_name) = LOWER(:clientName)
          AND type = :type AND price_group = :group AND one_time = 0 AND date <= :at
        ORDER BY date DESC LIMIT 1
    """)
    suspend fun getForClientAt(userId: Long, clientName: String, type: String, group: String, at: String): YukNarxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(narx: YukNarxEntity): Long
}

@Dao
interface AliasDao {
    @Query("SELECT * FROM aliases WHERE user_id = :userId")
    suspend fun getAll(userId: Long): List<AliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: AliasEntity)

    @Query("DELETE FROM aliases WHERE user_id = :userId AND alias = :alias")
    suspend fun delete(userId: Long, alias: String)
}

@Dao
interface RasxodDao {
    @Insert
    suspend fun insert(rasxod: RasxodEntity): Long

    @Query("""
        SELECT * FROM rasxod
        WHERE user_id = :userId AND date >= :start AND date < :end
        ORDER BY date
    """)
    suspend fun getRange(userId: Long, start: String, end: String): List<RasxodEntity>

    @Query("DELETE FROM rasxod WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("""
        SELECT SUM(amount) FROM rasxod
        WHERE user_id = :userId AND date >= :start AND date < :end
    """)
    suspend fun getTotalRange(userId: Long, start: String, end: String): Double?
}

@Dao
interface ClientReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ClientReminderEntity)

    @Query("DELETE FROM client_reminders WHERE user_id = :userId AND client_name = :clientName")
    suspend fun delete(userId: Long, clientName: String)

    @Query("SELECT * FROM client_reminders WHERE user_id = :userId AND days > 0")
    suspend fun getActive(userId: Long): List<ClientReminderEntity>
}

@Dao
interface ClientLimitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(limit: ClientLimitEntity)

    @Query("DELETE FROM client_limits WHERE user_id = :userId AND client_name = :clientName")
    suspend fun delete(userId: Long, clientName: String)

    @Query("SELECT * FROM client_limits WHERE user_id = :userId")
    suspend fun getAll(userId: Long): List<ClientLimitEntity>
}

@Dao
interface ClientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(client: ClientEntity): Long

    @Query("SELECT * FROM clients WHERE user_id = :userId")
    fun observeAll(userId: Long): Flow<List<ClientEntity>>

    @Query("DELETE FROM clients WHERE user_id = :userId AND LOWER(name) = LOWER(:name)")
    suspend fun deleteByName(userId: Long, name: String): Int
}

@Dao
interface DeletedTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: uz.daftar.app.data.db.entity.DeletedTransactionEntity): Long

    @Query("SELECT * FROM deleted_transactions WHERE user_id = :userId ORDER BY deleted_at DESC")
    suspend fun getAll(userId: Long): List<uz.daftar.app.data.db.entity.DeletedTransactionEntity>

    @Query("SELECT * FROM deleted_transactions WHERE id = :id")
    suspend fun getById(id: Long): uz.daftar.app.data.db.entity.DeletedTransactionEntity?

    @Query("DELETE FROM deleted_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 7 kundan oldingilarni tozalash — chaqirilganda ishlatiladi. */
    @Query("DELETE FROM deleted_transactions WHERE user_id = :userId AND deleted_at < :cutoff")
    suspend fun deleteOlderThan(userId: Long, cutoff: String): Int
}
