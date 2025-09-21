package com.contentedest.baby.di

import android.content.Context
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.contentedest.baby.data.local.AppDatabase
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.data.repo.SyncRepository
import com.contentedest.baby.net.TokenStorage
import com.contentedest.baby.sync.SyncWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Inject
import javax.inject.Provider
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
    fun provideEventRepository(db: AppDatabase): EventRepository = EventRepository(db.eventsDao(), db.syncStateDao())

    @Provides
    @Singleton
    fun provideTokenStorage(@ApplicationContext context: Context): TokenStorage = TokenStorage(context)

    @Provides
    @Singleton
    fun provideWorkerFactory(
        syncWorkerFactory: SyncWorker.Factory
    ): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? {
            return when (workerClassName) {
                SyncWorker::class.java.name -> syncWorkerFactory.create(appContext, workerParameters)
                else -> null
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface WorkerBindingModule {
    @dagger.Binds
    @IntoMap
    @dagger.hilt.work.WorkerKey(SyncWorker::class)
    fun bindSyncWorker(factory: SyncWorker.Factory): dagger.hilt.work.ChildWorkerFactory
}


