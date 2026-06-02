package uz.daftar.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ombor (sklad) yozuvi — kirim (yuk keldi) yoki chiqim (yuk chiqdi). */
@Entity(tableName = "sklad")
data class SkladEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val name: String,
    val qty: Double,
    val price: Double = 0.0,
    @ColumnInfo(name = "is_in") val isIn: Boolean = true,  // kirim=true, chiqim=false
    val date: Long = System.currentTimeMillis(),
    val note: String? = null
)
