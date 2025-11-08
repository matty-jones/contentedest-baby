package com.contentedest.baby.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.contentedest.baby.data.local.AppDatabase
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.data.repo.GrowthRepository
// import com.contentedest.baby.data.repo.SyncRepository // Assuming this isn't used directly in AppModule now
import com.contentedest.baby.net.TokenStorage
// SyncWorker is no longer directly bound here
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
// Removed dagger.hilt.work.WorkerKey and related imports as they are not needed with @HiltWorker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase {
        // Migration 1->2: add nullable 'details' column to events table
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `events` ADD COLUMN `details` TEXT")
            }
        }

        // Migration 2->3: add growth_data table
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `growth_data` (
                        `id` TEXT NOT NULL,
                        `device_id` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `value` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `ts` INTEGER NOT NULL,
                        `created_ts` INTEGER NOT NULL,
                        `updated_ts` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_growth_data_category` ON `growth_data` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_growth_data_ts` ON `growth_data` (`ts`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_growth_data_device_id` ON `growth_data` (`device_id`)")
            }
        }

        return Room.databaseBuilder(context, AppDatabase::class.java, "tcb.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @Provides
    @Singleton
    fun provideEventRepository(db: AppDatabase): EventRepository = EventRepository(db.eventsDao(), db.syncStateDao())

    @Provides
    @Singleton
    fun provideGrowthRepository(db: AppDatabase, api: com.contentedest.baby.net.ApiService): GrowthRepository = 
        GrowthRepository(db.growthDataDao(), api)

    @Provides
    @Singleton
    fun provideTokenStorage(@ApplicationContext context: Context): TokenStorage = TokenStorage(context)

}

// The WorkerBindingModule interface has been removed entirely.
// Hilt will automatically provide the factory for SyncWorker when @HiltWorker is used.
