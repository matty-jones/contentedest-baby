package com.contentedest.baby.net

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @GET("/healthz")
    suspend fun healthz(): Healthz

    @POST("/pair")
    suspend fun pair(@Body req: PairRequest): PairResponse
}
