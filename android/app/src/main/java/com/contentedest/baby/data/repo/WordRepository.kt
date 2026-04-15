package com.contentedest.baby.data.repo

import com.contentedest.baby.data.local.BabyWordDao
import com.contentedest.baby.data.local.BabyWordEntity
import com.contentedest.baby.net.ApiService
import com.contentedest.baby.net.WordDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class WordRepository(
    private val babyWordDao: BabyWordDao,
    private val api: ApiService
) {
    suspend fun insertWord(deviceId: String, word: String, ts: Long): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis() / 1000
        val entity = BabyWordEntity(
            id = id,
            device_id = deviceId,
            word = word,
            ts = ts,
            created_ts = now,
            updated_ts = now,
            version = 1,
            deleted = false
        )
        babyWordDao.upsert(entity)
        syncPush(entity.toDto())
        id
    }

    private fun BabyWordEntity.toDto(): WordDto {
        return WordDto(
            id = id,
            deviceId = device_id,
            word = word,
            ts = ts,
            createdTs = created_ts,
            updatedTs = updated_ts,
            version = version,
            deleted = deleted
        )
    }

    private fun WordDto.toEntity(): BabyWordEntity {
        return BabyWordEntity(
            id = id,
            device_id = deviceId,
            word = word,
            ts = ts,
            created_ts = createdTs,
            updated_ts = updatedTs,
            version = version,
            deleted = deleted
        )
    }

    suspend fun getAllOrderedByFirstUseDesc(): List<BabyWordEntity> = withContext(Dispatchers.IO) {
        babyWordDao.getAllOrderedByFirstUseDesc()
    }

    suspend fun getAllOrderedByFirstUseAsc(): List<BabyWordEntity> = withContext(Dispatchers.IO) {
        babyWordDao.getAllOrderedByFirstUseAsc()
    }

    suspend fun getAll(): List<BabyWordEntity> = withContext(Dispatchers.IO) {
        babyWordDao.getAllOrderedByFirstUseAsc()
    }

    suspend fun hasWordCaseInsensitive(word: String): Boolean = withContext(Dispatchers.IO) {
        val t = word.trim()
        if (t.isEmpty()) return@withContext false
        val lower = t.lowercase()
        babyWordDao.getAllOrderedByFirstUseDesc().any { it.word.trim().lowercase() == lower }
    }

    suspend fun hasWordCaseInsensitiveExceptId(word: String, excludedId: String): Boolean = withContext(Dispatchers.IO) {
        val t = word.trim()
        if (t.isEmpty()) return@withContext false
        val lower = t.lowercase()
        babyWordDao.getAllOrderedByFirstUseDesc().any {
            it.id != excludedId && it.word.trim().lowercase() == lower
        }
    }

    suspend fun updateWord(id: String, word: String, ts: Long) = withContext(Dispatchers.IO) {
        val existing = babyWordDao.getById(id) ?: return@withContext
        val now = System.currentTimeMillis() / 1000
        val updated = existing.copy(
            word = word,
            ts = ts,
            updated_ts = now,
            version = existing.version + 1
        )
        babyWordDao.upsert(updated)
        syncPush(updated.toDto())
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        babyWordDao.softDelete(id, now)
        val row = babyWordDao.getById(id) ?: return@withContext
        syncPush(row.toDto())
    }

    suspend fun syncPush(data: WordDto): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val response = api.pushWord(data)
            val merged = data.toEntity().copy(
                updated_ts = response.data.updatedTs,
                version = response.data.version
            )
            babyWordDao.upsert(merged)
            Result.Success(response.serverClock)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    suspend fun syncPull(since: Long = 0): Result<Pair<Long, List<WordDto>>> = withContext(Dispatchers.IO) {
        try {
            val response = api.pullWords(since)
            response.data.forEach { dto ->
                babyWordDao.upsert(dto.toEntity())
            }
            Result.Success(response.serverClock to response.data)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
