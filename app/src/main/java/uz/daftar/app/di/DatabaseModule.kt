package uz.daftar.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uz.daftar.app.data.db.DaftarDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Singleton

/** v2 -> v3: eslatma va sklad jadvallarini qo'shadi (mavjud ma'lumot saqlanadi) */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `eslatma` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`user_id` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`trigger_at` INTEGER NOT NULL, " +
                "`done` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sklad` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`user_id` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`qty` REAL NOT NULL, " +
                "`price` REAL NOT NULL, " +
                "`is_in` INTEGER NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "`note` TEXT)"
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DB_VERSION = 3

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DaftarDatabase {
        // Migration OLDIDAN zaxira: diskdagi baza versiyasi koddan eski bo'lsa,
        // har ehtimolga qarshi .db faylni nusxalaymiz (ma'lumot yo'qolmasin).
        runCatching {
            val dbFile = context.getDatabasePath(DaftarDatabase.NAME)
            if (dbFile.exists()) {
                val ver = android.database.sqlite.SQLiteDatabase
                    .openDatabase(dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
                    .use { it.version }
                if (ver in 1 until DB_VERSION) {
                    val dir = java.io.File(context.filesDir, "backups").apply { mkdirs() }
                    val ts = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                    dbFile.copyTo(java.io.File(dir, "premigration_v${ver}_$ts.db"), overwrite = true)
                }
            }
        }
        // fallbackToDestructiveMigration OLIB TASHLANDI — baza endi hech qachon avtomatik
        // o'chmaydi. Yangi versiya kerak bo'lsa Migration yozish SHART (aks holda ochilishda
        // xato beradi — bu yaxshi: jim turib ma'lumotni yo'q qilmaydi).
        return Room.databaseBuilder(
            context,
            DaftarDatabase::class.java,
            DaftarDatabase.NAME
        )
            .addMigrations(MIGRATION_2_3)
            .build()
    }

    @Provides fun txDao(db: DaftarDatabase) = db.transactionDao()
    @Provides fun priceDao(db: DaftarDatabase) = db.priceHistoryDao()
    @Provides fun clientPriceDao(db: DaftarDatabase) = db.clientPriceDao()
    @Provides fun yukNarxDao(db: DaftarDatabase) = db.yukNarxDao()
    @Provides fun aliasDao(db: DaftarDatabase) = db.aliasDao()
    @Provides fun rasxodDao(db: DaftarDatabase) = db.rasxodDao()
    @Provides fun reminderDao(db: DaftarDatabase) = db.clientReminderDao()
    @Provides fun limitDao(db: DaftarDatabase) = db.clientLimitDao()
    @Provides fun clientDao(db: DaftarDatabase) = db.clientDao()
    @Provides fun deletedTxDao(db: DaftarDatabase) = db.deletedTxDao()
    @Provides fun eslatmaDao(db: DaftarDatabase) = db.eslatmaDao()
    @Provides fun skladDao(db: DaftarDatabase) = db.skladDao()
}
