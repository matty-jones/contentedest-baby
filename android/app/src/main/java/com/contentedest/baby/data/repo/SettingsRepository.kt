package com.contentedest.baby.data.repo

import com.contentedest.baby.data.local.SettingsDao
import com.contentedest.baby.data.local.SettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsRepository(
    private val settingsDao: SettingsDao
) {
    suspend fun get(): SettingsEntity? = withContext(Dispatchers.IO) {
        settingsDao.get()
    }

    suspend fun getOrCreate(): SettingsEntity = withContext(Dispatchers.IO) {
        settingsDao.get() ?: SettingsEntity().also { settingsDao.upsert(it) }
    }

    suspend fun getDobEpochDays(): Int? = withContext(Dispatchers.IO) {
        settingsDao.get()?.dob_epoch_days
    }

    suspend fun setDobEpochDays(epochDays: Int?) = withContext(Dispatchers.IO) {
        val current = settingsDao.get() ?: SettingsEntity()
        settingsDao.upsert(current.copy(dob_epoch_days = epochDays))
    }
}
