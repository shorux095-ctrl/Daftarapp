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

    @Query("DELETE FROM sklad WHERE id = :id")
    suspend fun delete(id: Long)
}
