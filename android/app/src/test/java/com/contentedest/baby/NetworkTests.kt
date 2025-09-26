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
        assertEquals("dev1", resp.deviceId)
        assertEquals("t123", resp.token)
    }

    @Test
    fun syncPush() {
        server.enqueue(MockResponse().setBody("{\"serverClock\": 123, \"results\": []}").setHeader("Content-Type", "application/json"))
        val events = listOf(
            com.contentedest.baby.net.EventDto(
                eventId = "e1",
                type = "sleep",
                start_ts = 1000L,
                end_ts = 2000L,
                created_ts = 1000L,
                updated_ts = 2000L,
                version = 1,
                device_id = "dev1"
            )
        )
        val resp = kotlinx.coroutines.runBlocking { api.syncPush(events) }
        assertEquals(123L, resp.serverClock)
        assertEquals(0, resp.results.size)
    }

    @Test
    fun syncPull() {
        val eventsJson = "[{\"eventId\": \"e1\", \"type\": \"sleep\", \"start_ts\": 1000, \"end_ts\": 2000, \"created_ts\": 1000, \"updated_ts\": 2000, \"version\": 1, \"device_id\": \"dev1\"}]"
        server.enqueue(MockResponse().setBody("{\"serverClock\": 123, \"events\": $eventsJson}").setHeader("Content-Type", "application/json"))
        val resp = kotlinx.coroutines.runBlocking { api.syncPull(100L) }
        assertEquals(123L, resp.serverClock)
        assertEquals(1, resp.events.size)
        assertEquals("e1", resp.events[0].eventId)
    }
}
