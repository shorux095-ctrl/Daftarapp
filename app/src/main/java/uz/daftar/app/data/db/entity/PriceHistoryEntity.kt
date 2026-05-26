package uz.daftar.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_history",
    indices = [Index(value = ["user_id", "client_name", "price_type", "date"])]
)
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(name = "price_type") val priceType: String,
    val price: Double,
    val date: String
)
