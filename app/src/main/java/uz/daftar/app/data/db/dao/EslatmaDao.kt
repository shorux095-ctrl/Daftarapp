package uz.daftar.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uz.daftar.app.data.db.entity.EslatmaEntity

@Dao
interface EslatmaDao {
    @Insert
    suspend fun insert(e: EslatmaEntity): Long

    @Query("SELECT * FROM eslatma WHERE user_id = :userId ORDER BY trigger_at ASC")
    fun all(userId: Long): Flow<List<EslatmaEntity>>

    @Query("DELETE FROM eslatma WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE eslatma SET done = 1 WHERE id = :id")
    suspend fun markDone(id: Long)
}
