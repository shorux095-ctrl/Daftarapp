package uz.daftar.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "aliases", primaryKeys = ["user_id", "alias"])
data class AliasEntity(
    @ColumnInfo(name = "user_id") val userId: Long,
    val alias: String,
    val canon: String
)

@Entity(tableName = "rasxod")
data class RasxodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val amount: Double,
    @ColumnInfo(defaultValue = "''") val note: String = "",
    val date: String
)

@Entity(tableName = "yuk_rasxod_narx", primaryKeys = ["user_id", "type"])
data class YukRasxodNarxEntity(
    @ColumnInfo(name = "user_id") val userId: Long,
    val type: String,
    @ColumnInfo(defaultValue = "0") val cost: Double = 0.0,
    val updated: String? = null
)

@Entity(tableName = "reminder_log", primaryKeys = ["user_id", "date", "days_cat"])
data class ReminderLogEntity(
    @ColumnInfo(name = "user_id") val userId: Long,
    val date: String,
    @ColumnInfo(name = "days_cat") val daysCat: Int
)

@Entity(tableName = "auto_daily_log", primaryKeys = ["user_id", "date"])
data class AutoDailyLogEntity(
    @ColumnInfo(name = "user_id") val userId: Long,
    val date: String
)

@Entity(tableName = "auto_monthly_log", primaryKeys = ["user_id", "ym"])
data class AutoMonthlyLogEntity(
    @ColumnInfo(name = "user_id") val userId: Long,
    val ym: String
)

@Entity(tableName = "backup_messages", primaryKeys = ["user_id", "message_id"])
data class BackupMessageEntity(
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "chat_id") val chatId: Long,
    val date: String
)

@Entity(tableName = "deleted_transactions")
data class DeletedTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "client_name") val clientName: String,
    val type: String,
    val amount: Double,
    val date: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String,
    @ColumnInfo(defaultValue = "''") val note: String = ""
)

@Entity(tableName = "client_reminders", primaryKeys = ["user_id", "client_name"])
data class ClientReminderEntity(
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(defaultValue = "7") val days: Int = 7
)

@Entity(tableName = "client_debt_cache", primaryKeys = ["user_id", "client_name"])
data class ClientDebtCacheEntity(
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(defaultValue = "0") val debt: Double = 0.0,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "client_limits", primaryKeys = ["user_id", "client_name"])
data class ClientLimitEntity(
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(name = "limit_amt") val limitAmt: Double,
    val created: String? = null
)
