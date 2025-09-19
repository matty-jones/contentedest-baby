package com.contentedest.baby

import com.contentedest.baby.net.ApiService
import com.contentedest.baby.net.PairRequest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class NetworkTests {
    private lateinit var server: MockWebServer
    private lateinit var api: ApiService

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create())
            .client(client)
            .build()
        api = retrofit.create(ApiService::class.java)
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun healthz() {
        server.enqueue(MockResponse().setBody("{\"status\":\"ok\"}").setHeader("Content-Type", "application/json"))
        val resp = kotlinx.coroutines.runBlocking { api.healthz() }
        assertEquals("ok", resp.status)
    }

    @Test
    fun pairing() {
        server.enqueue(MockResponse().setBody("{\"device_id\":\"dev1\",\"token\":\"t123\"}").setHeader("Content-Type", "application/json"))
        val resp = kotlinx.coroutines.runBlocking { api.pair(PairRequest("code", "dev1", "Phone")) }
        assertEquals("dev1", resp.device_id)
        assertEquals("t123", resp.token)
    }
}
