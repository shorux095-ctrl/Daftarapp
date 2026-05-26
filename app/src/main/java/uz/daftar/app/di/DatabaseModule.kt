package uz.daftar.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uz.daftar.app.data.db.DaftarDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DaftarDatabase {
        return Room.databaseBuilder(
            context,
            DaftarDatabase::class.java,
            DaftarDatabase.NAME
        )
            // Boshlangich versiyada: agar sxema mos kelmasa, qayta yarat
            // Production'da: Migration.MIGRATION_X_Y orqali to'g'ri migratsiya kerak
            .fallbackToDestructiveMigration()
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
}
