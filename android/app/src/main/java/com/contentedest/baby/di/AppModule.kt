package com.contentedest.baby.di

import android.content.Context
import androidx.room.Room
import com.contentedest.baby.data.local.AppDatabase
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.net.TokenStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "tcb.db").build()

    @Provides
    @Singleton
    fun provideEventRepository(db: AppDatabase): EventRepository = EventRepository(db.eventsDao())

    @Provides
    @Singleton
    fun provideTokenStorage(@ApplicationContext context: Context): TokenStorage = TokenStorage(context)
}


