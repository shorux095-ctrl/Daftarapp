package uz.daftar.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tranzaksiya: yuk (a/b/c/d/k), to'lov (p), qarz (q).
 * Bot.py'dagi transactions jadval bilan bir xil sxema.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["user_id", "client_name"]),
        Index(value = ["user_id", "date"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "client_name") val clientName: String,
    val type: String,          // 'a', 'b', 'c', 'd', 'k', 'p', 'q'
    val amount: Double,
    val date: String,          // ISO-8601 string (bot.py bilan moslik uchun)
    @ColumnInfo(name = "t_override") val tOverride: Double? = null
)
