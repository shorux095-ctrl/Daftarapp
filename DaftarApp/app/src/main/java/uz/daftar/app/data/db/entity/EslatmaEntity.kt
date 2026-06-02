package uz.daftar.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Foydalanuvchi qo'ygan eslatma (bildirishnoma vaqti bilan). */
@Entity(tableName = "eslatma")
data class EslatmaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val text: String,
    @ColumnInfo(name = "trigger_at") val triggerAt: Long,   // epoch millis
    val done: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
