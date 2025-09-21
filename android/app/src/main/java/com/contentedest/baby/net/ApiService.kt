package com.contentedest.baby.net

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @GET("/healthz")
    suspend fun healthz(): Healthz

    @POST("/pair")
    suspend fun pair(@Body req: PairRequest): PairResponse

    @POST("/sync/push")
    suspend fun syncPush(@Body events: List<EventDto>): SyncPushResponse

    @GET("/sync/pull")
    suspend fun syncPull(@Query("since") since: Long): SyncPullResponse
}
