package dev.kosh.financetracker.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kosh.financetracker.data.database.FinanceDatabase
import dev.kosh.financetracker.data.database.MerchantCategoryMappingDao
import dev.kosh.financetracker.data.database.TransactionDao
import dev.kosh.financetracker.data.repository.RoomTransactionRepository
import dev.kosh.financetracker.data.repository.TransactionRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, vault: dev.kosh.financetracker.security.Vault): FinanceDatabase {
        // The database only exists while the app is unlocked. Anything that needs it before
        // that (a bug) fails loudly instead of silently opening plaintext storage.
        val passphrase = vault.databasePassphrase() ?: error("Database requested while the app is locked")
        dev.kosh.financetracker.security.DatabaseEncryptor.loadNative()
        return Room.databaseBuilder(context, FinanceDatabase::class.java, FinanceDatabase.DB_NAME)
            .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase))
            .addMigrations(FinanceDatabase.MIGRATION_7_8, FinanceDatabase.MIGRATION_8_9)
            .build()
    }

    @Provides
    fun provideTransactionDao(database: FinanceDatabase): TransactionDao =
        database.transactionDao()

    @Provides
    fun provideAccountDao(database: FinanceDatabase): dev.kosh.financetracker.data.database.AccountDao =
        database.accountDao()

    @Provides
    fun provideMerchantCategoryMappingDao(database: FinanceDatabase): MerchantCategoryMappingDao =
        database.merchantCategoryMappingDao()

    @Provides
    @Singleton
    fun provideTransactionRepository(
        dao: TransactionDao,
        merchantCategoryMappingDao: MerchantCategoryMappingDao,
        database: FinanceDatabase,
    ): TransactionRepository = RoomTransactionRepository(dao, merchantCategoryMappingDao, database)
}
