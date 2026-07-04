package uz.daftar.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import uz.daftar.app.data.db.dao.AliasDao
import uz.daftar.app.data.db.dao.ClientDao
import uz.daftar.app.data.db.dao.ClientLimitDao
import uz.daftar.app.data.db.dao.ClientPriceDao
import uz.daftar.app.data.db.dao.ClientReminderDao
import uz.daftar.app.data.db.dao.DeletedTransactionDao
import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.RasxodDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.dao.YukNarxDao
import uz.daftar.app.data.db.entity.AliasEntity
import uz.daftar.app.data.db.entity.AutoDailyLogEntity
import uz.daftar.app.data.db.entity.AutoMonthlyLogEntity
import uz.daftar.app.data.db.entity.BackupMessageEntity
import uz.daftar.app.data.db.entity.ClientDebtCacheEntity
import uz.daftar.app.data.db.entity.ClientEntity
import uz.daftar.app.data.db.entity.ClientLimitEntity
import uz.daftar.app.data.db.entity.EslatmaEntity
import uz.daftar.app.data.db.entity.SkladEntity
import uz.daftar.app.data.db.entity.ClientPriceEntity
import uz.daftar.app.data.db.entity.ClientReminderEntity
import uz.daftar.app.data.db.entity.DeletedTransactionEntity
import uz.daftar.app.data.db.entity.PriceHistoryEntity
import uz.daftar.app.data.db.entity.RasxodEntity
import uz.daftar.app.data.db.entity.ReminderLogEntity
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.data.db.entity.YukNarxEntity
import uz.daftar.app.data.db.entity.YukRasxodNarxEntity

@Database(
    entities = [
        ClientEntity::class,
        TransactionEntity::class,
        PriceHistoryEntity::class,
        ClientPriceEntity::class,
        YukNarxEntity::class,
        AliasEntity::class,
        RasxodEntity::class,
        YukRasxodNarxEntity::class,
        ReminderLogEntity::class,
        AutoDailyLogEntity::class,
        AutoMonthlyLogEntity::class,
        BackupMessageEntity::class,
        DeletedTransactionEntity::class,
        ClientReminderEntity::class,
        ClientDebtCacheEntity::class,
        ClientLimitEntity::class,
        EslatmaEntity::class,
        SkladEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class DaftarDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun priceHistoryDao(): PriceHistoryDao
    abstract fun clientPriceDao(): ClientPriceDao
    abstract fun yukNarxDao(): YukNarxDao
    abstract fun aliasDao(): AliasDao
    abstract fun rasxodDao(): RasxodDao
    abstract fun clientReminderDao(): ClientReminderDao
    abstract fun clientLimitDao(): ClientLimitDao
    abstract fun clientDao(): ClientDao
    abstract fun deletedTxDao(): DeletedTransactionDao
    abstract fun eslatmaDao(): uz.daftar.app.data.db.dao.EslatmaDao
    abstract fun skladDao(): uz.daftar.app.data.db.dao.SkladDao

    companion object {
        const val NAME = "daftar.db"
    }
}
