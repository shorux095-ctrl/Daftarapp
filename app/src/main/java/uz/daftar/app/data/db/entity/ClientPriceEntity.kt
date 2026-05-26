package uz.daftar.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "client_prices",
    primaryKeys = ["user_id", "client_name"]
)
data class ClientPriceEntity(
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(name = "a_price") val aPrice: Double? = null,
    @ColumnInfo(name = "b_price") val bPrice: Double? = null,
    @ColumnInfo(name = "c_price") val cPrice: Double? = null,
    @ColumnInfo(name = "d_price") val dPrice: Double? = null,
    @ColumnInfo(name = "k_price") val kPrice: Double? = null,
    @ColumnInfo(name = "p_price") val pPrice: Double? = null,
    @ColumnInfo(name = "q_price") val qPrice: Double? = null
)
