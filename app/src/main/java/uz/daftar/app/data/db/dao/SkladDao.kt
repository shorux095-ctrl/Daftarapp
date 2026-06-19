package uz.daftar.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uz.daftar.app.data.db.entity.SkladEntity

@Dao
interface SkladDao {
    @Insert
    suspend fun insert(e: SkladEntity): Long

    @Query("SELECT * FROM sklad WHERE user_id = :userId ORDER BY date DESC")
    fun all(userId: Long): Flow<List<SkladEntity>>

    @Query("UPDATE sklad SET name = :name, qty = :qty, price = :price, is_in = :isIn, date = :dateMs WHERE id = :id")
    suspend fun update(id: Long, name: String, qty: Double, price: Double, isIn: Boolean, dateMs: Long)

    @Query("DELETE FROM sklad WHERE id = :id")
    suspend fun delete(id: Long)
}
