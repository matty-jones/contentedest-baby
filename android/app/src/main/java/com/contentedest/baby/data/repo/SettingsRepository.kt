package com.contentedest.baby.data.repo

import com.contentedest.baby.data.local.SettingsDao
import com.contentedest.baby.data.local.SettingsEntity
import com.contentedest.baby.net.ApiService
import com.contentedest.baby.net.BabyProfileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepository(
    private val settingsDao: SettingsDao,
    private val api: ApiService
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

    fun observeDobEpochDays(): Flow<Int?> =
        settingsDao.observe().map { it?.dob_epoch_days }

    /**
     * Persist DOB locally and push to the shared server baby profile so all devices converge.
     */
    suspend fun setDobEpochDays(epochDays: Int?, deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val current = settingsDao.get() ?: SettingsEntity()
        val now = System.currentTimeMillis() / 1000
        val updated = current.copy(
            dob_epoch_days = epochDays,
            dob_updated_ts = now,
            dob_version = current.dob_version + 1,
            dob_device_id = deviceId
        )
        settingsDao.upsert(updated)
        when (val push = syncPush(updated)) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(push.exception)
        }
    }

    suspend fun syncPull(): Result<BabyProfileDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.pullBabyProfile()
            applyServerProfile(response.data)
            Result.Success(response.data)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * After pull: if this device has a DOB the server lacks (or only a legacy local DOB), push it.
     */
    suspend fun syncPushLocalIfNeeded(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        var local = settingsDao.get() ?: return@withContext Result.Success(Unit)
        if (local.dob_epoch_days == null) return@withContext Result.Success(Unit)

        if (local.dob_version <= 0) {
            val now = System.currentTimeMillis() / 1000
            local = local.copy(
                dob_updated_ts = if (local.dob_updated_ts > 0) local.dob_updated_ts else now,
                dob_version = 1,
                dob_device_id = local.dob_device_id?.takeIf { it.isNotBlank() } ?: deviceId
            )
            settingsDao.upsert(local)
        }

        when (val push = syncPush(local)) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(push.exception)
        }
    }

    private suspend fun syncPush(local: SettingsEntity): Result<Long> {
        return try {
            val dto = BabyProfileDto(
                dobEpochDays = local.dob_epoch_days,
                updatedTs = local.dob_updated_ts,
                version = local.dob_version,
                deviceId = local.dob_device_id.orEmpty()
            )
            val response = api.pushBabyProfile(dto)
            applyServerProfile(response.data)
            Result.Success(response.serverClock)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private suspend fun applyServerProfile(data: BabyProfileDto) {
        val local = settingsDao.get() ?: SettingsEntity()
        val remoteWins = profileWins(
            remoteVersion = data.version,
            remoteUpdatedTs = data.updatedTs,
            remoteDeviceId = data.deviceId,
            localVersion = local.dob_version,
            localUpdatedTs = local.dob_updated_ts,
            localDeviceId = local.dob_device_id.orEmpty()
        )
        if (!remoteWins) return
        settingsDao.upsert(
            local.copy(
                dob_epoch_days = data.dobEpochDays,
                dob_updated_ts = data.updatedTs,
                dob_version = data.version,
                dob_device_id = data.deviceId.ifEmpty { local.dob_device_id }
            )
        )
    }

    companion object {
        fun profileWins(
            remoteVersion: Int,
            remoteUpdatedTs: Long,
            remoteDeviceId: String,
            localVersion: Int,
            localUpdatedTs: Long,
            localDeviceId: String
        ): Boolean {
            if (remoteVersion != localVersion) return remoteVersion > localVersion
            if (remoteUpdatedTs != localUpdatedTs) return remoteUpdatedTs > localUpdatedTs
            return remoteDeviceId >= localDeviceId
        }
    }
}
