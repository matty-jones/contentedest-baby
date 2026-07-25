package com.contentedest.baby.data.repo

import android.content.Context
import com.contentedest.baby.data.local.BabyWordDao
import com.contentedest.baby.data.local.BabyWordEntity
import com.contentedest.baby.net.ApiService
import com.contentedest.baby.net.WordDto
import com.contentedest.baby.ui.words.WordFuzzyMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class WordRepository(
    private val babyWordDao: BabyWordDao,
    private val api: ApiService
) {
    suspend fun insertWord(
        deviceId: String,
        word: String,
        ts: Long,
        understands: Boolean = false,
        says: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val (u, s) = normalizeMabFlags(understands, says)
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
            deleted = false,
            understands = u,
            says = s
        )
        babyWordDao.upsert(entity)
        syncPush(entity.toDto())
        id
    }

    /**
     * Insert a new word, or if a case-insensitive duplicate exists, upsert MA-B flags
     * when they differ. Returns the row id and whether a new row was created.
     */
    suspend fun insertOrUpsertMab(
        deviceId: String,
        word: String,
        ts: Long,
        understands: Boolean,
        says: Boolean
    ): InsertResult = withContext(Dispatchers.IO) {
        val (u, s) = normalizeMabFlags(understands, says)
        val existing = findByWordCaseInsensitive(word)
        if (existing != null) {
            if (existing.understands == u && existing.says == s) {
                return@withContext InsertResult.Duplicate(existing.id)
            }
            val now = System.currentTimeMillis() / 1000
            val updated = existing.copy(
                understands = u,
                says = s,
                updated_ts = now,
                version = existing.version + 1
            )
            babyWordDao.upsert(updated)
            syncPush(updated.toDto())
            return@withContext InsertResult.UpdatedMab(updated.id)
        }
        val id = insertWord(deviceId, word, ts, u, s)
        InsertResult.Created(id)
    }

    sealed class InsertResult {
        data class Created(val id: String) : InsertResult()
        data class UpdatedMab(val id: String) : InsertResult()
        data class Duplicate(val id: String) : InsertResult()
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
            deleted = deleted,
            understands = understands,
            says = says
        )
    }

    private fun WordDto.toEntity(): BabyWordEntity {
        val (u, s) = normalizeMabFlags(understands, says)
        return BabyWordEntity(
            id = id,
            device_id = deviceId,
            word = word,
            ts = ts,
            created_ts = createdTs,
            updated_ts = updatedTs,
            version = version,
            deleted = deleted,
            understands = u,
            says = s
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

    suspend fun findByWordCaseInsensitive(word: String): BabyWordEntity? = withContext(Dispatchers.IO) {
        val t = word.trim()
        if (t.isEmpty()) return@withContext null
        val lower = t.lowercase()
        babyWordDao.getAllOrderedByFirstUseDesc().firstOrNull { it.word.trim().lowercase() == lower }
    }

    suspend fun hasWordCaseInsensitive(word: String): Boolean =
        findByWordCaseInsensitive(word) != null

    suspend fun hasWordCaseInsensitiveExceptId(word: String, excludedId: String): Boolean = withContext(Dispatchers.IO) {
        val t = word.trim()
        if (t.isEmpty()) return@withContext false
        val lower = t.lowercase()
        babyWordDao.getAllOrderedByFirstUseDesc().any {
            it.id != excludedId && it.word.trim().lowercase() == lower
        }
    }

    suspend fun updateWord(
        id: String,
        word: String,
        ts: Long,
        understands: Boolean = false,
        says: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val existing = babyWordDao.getById(id) ?: return@withContext
        val (u, s) = normalizeMabFlags(understands, says)
        val now = System.currentTimeMillis() / 1000
        val updated = existing.copy(
            word = word,
            ts = ts,
            understands = u,
            says = s,
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
                version = response.data.version,
                understands = response.data.understands,
                says = response.data.says
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

    /**
     * One-time backfill: mark existing vocabulary that fuzzy-matches MA-B as Said.
     */
    suspend fun runMabSaysBackfillIfNeeded(context: Context): Int = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MAB_BACKFILL_DONE, false)) return@withContext 0

        val all = babyWordDao.getAllOrderedByFirstUseAsc()
        var updatedCount = 0
        val now = System.currentTimeMillis() / 1000
        for (entity in all) {
            if (!WordFuzzyMatcher.matchesMab(entity.word)) continue
            if (entity.says && entity.understands) continue
            val updated = entity.copy(
                understands = true,
                says = true,
                updated_ts = now,
                version = entity.version + 1
            )
            babyWordDao.upsert(updated)
            syncPush(updated.toDto())
            updatedCount++
        }
        prefs.edit().putBoolean(KEY_MAB_BACKFILL_DONE, true).apply()
        updatedCount
    }

    companion object {
        private const val PREFS_NAME = "words_prefs"
        private const val KEY_MAB_BACKFILL_DONE = "mab_says_backfill_done"

        fun normalizeMabFlags(understands: Boolean, says: Boolean): Pair<Boolean, Boolean> {
            return if (says) true to true else understands to false
        }
    }
}
