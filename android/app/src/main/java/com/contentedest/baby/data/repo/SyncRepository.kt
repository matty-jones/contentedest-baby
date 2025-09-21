package com.contentedest.baby.data.repo

import com.contentedest.baby.net.ApiService
import com.contentedest.baby.net.EventDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncRepository(private val api: ApiService) {
    suspend fun syncPush(events: List<EventDto>): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val response = api.syncPush(events)
            Result.success(response.server_clock)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncPull(since: Long): Result<Pair<Long, List<EventDto>>> = withContext(Dispatchers.IO) {
        try {
            val response = api.syncPull(since)
            Result.success(response.server_clock to response.events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
