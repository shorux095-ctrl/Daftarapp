package uz.daftar.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "yuk_narx",
    indices = [Index(value = ["user_id", "type", "date"])]
)
data class YukNarxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "client_name") val clientName: String? = null,
    val type: String,
    val price: Double,
    val date: String,
    @ColumnInfo(name = "one_time", defaultValue = "0") val oneTime: Int = 0,
    @ColumnInfo(name = "price_group", defaultValue = "'t'") val priceGroup: String = "t"
)
