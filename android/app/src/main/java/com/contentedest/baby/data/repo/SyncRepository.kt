package com.contentedest.baby.data.repo

import com.contentedest.baby.net.ApiService
import com.contentedest.baby.net.EventDto
import com.contentedest.baby.data.repo.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncRepository(private val api: ApiService) {
    suspend fun syncPush(events: List<EventDto>): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val response = api.syncPush(events)
            Result.Success(response.server_clock)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    suspend fun syncPull(since: Long): Result<Pair<Long, List<EventDto>>> = withContext(Dispatchers.IO) {
        try {
            val response = api.syncPull(since)
            Result.Success(response.server_clock to response.events)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
