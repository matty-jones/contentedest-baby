package com.contentedest.baby.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.contentedest.baby.data.local.AppDatabase
import com.contentedest.baby.data.repo.EventRepository
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

        return Room.databaseBuilder(context, AppDatabase::class.java, "tcb.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideEventRepository(db: AppDatabase): EventRepository = EventRepository(db.eventsDao(), db.syncStateDao())

    @Provides
    @Singleton
    fun provideTokenStorage(@ApplicationContext context: Context): TokenStorage = TokenStorage(context)

}

// The WorkerBindingModule interface has been removed entirely.
// Hilt will automatically provide the factory for SyncWorker when @HiltWorker is used.
