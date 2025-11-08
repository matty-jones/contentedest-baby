package com.contentedest.baby.data.repo

import com.contentedest.baby.data.local.*
import com.contentedest.baby.net.GrowthDataDto
import com.contentedest.baby.net.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class GrowthRepository(
    private val growthDataDao: GrowthDataDao,
    private val api: ApiService
) {
    suspend fun insertGrowthData(
        deviceId: String,
        category: GrowthCategory,
        value: Double,
        unit: String,
        ts: Long
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis() / 1000
        val entity = GrowthDataEntity(
            id = id,
            device_id = deviceId,
            category = category,
            value = value,
            unit = unit,
            ts = ts,
            created_ts = now,
            updated_ts = now,
            version = 1,
            deleted = false
        )
        growthDataDao.upsert(entity)
        id
    }

    suspend fun getByCategory(category: GrowthCategory): List<GrowthDataEntity> = withContext(Dispatchers.IO) {
        growthDataDao.getByCategory(category)
    }

    suspend fun getAll(): List<GrowthDataEntity> = withContext(Dispatchers.IO) {
        growthDataDao.getAll()
    }

    suspend fun getById(id: String): GrowthDataEntity? = withContext(Dispatchers.IO) {
        growthDataDao.getById(id)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        growthDataDao.softDelete(id, now)
    }

    // Sync methods
    suspend fun syncPush(data: GrowthDataDto): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val response = api.pushGrowthData(data)
            // Update local entity with server response
            val entity = data.toEntity()
            entity.copy(
                updated_ts = response.data.updatedTs,
                version = response.data.version
            ).let { growthDataDao.upsert(it) }
            Result.Success(response.serverClock)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    suspend fun syncPull(category: String? = null, since: Long = 0): Result<Pair<Long, List<GrowthDataDto>>> = withContext(Dispatchers.IO) {
        try {
            val response = api.pullGrowthData(category, since)
            // Store pulled data locally
            response.data.forEach { dto ->
                growthDataDao.upsert(dto.toEntity())
            }
            Result.Success(response.serverClock to response.data)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    // Convert GrowthDataEntity to GrowthDataDto for API
    fun GrowthDataEntity.toDto(): GrowthDataDto {
        return GrowthDataDto(
            id = id,
            deviceId = device_id,
            category = category.name,
            value = value,
            unit = unit,
            ts = ts,
            createdTs = created_ts,
            updatedTs = updated_ts,
            version = version,
            deleted = deleted
        )
    }

    // Convert GrowthDataDto to GrowthDataEntity for local storage
    fun GrowthDataDto.toEntity(): GrowthDataEntity {
        return GrowthDataEntity(
            id = id,
            device_id = deviceId,
            category = GrowthCategory.valueOf(category),
            value = value,
            unit = unit,
            ts = ts,
            created_ts = createdTs,
            updated_ts = updatedTs,
            version = version,
            deleted = deleted
        )
    }
}

