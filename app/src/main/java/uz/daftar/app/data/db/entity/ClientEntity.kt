package uz.daftar.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val a: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val b: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val c: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val p: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val q: Double = 0.0
)
