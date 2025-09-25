package com.contentedest.baby.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PairRequest(val pairing_code: String, val device_id: String, val name: String? = null)

@Serializable
data class PairResponse(val device_id: String, val token: String)

@Serializable
data class Healthz(val status: String)

@Serializable
data class EventDto(
    val event_id: String,
    val type: String,
    val payload: JsonElement? = null,
    val start_ts: Long? = null,
    val end_ts: Long? = null,
    val ts: Long? = null,
    val created_ts: Long,
    val updated_ts: Long,
    val version: Int,
    val deleted: Boolean = false,
    val device_id: String
)

@Serializable
data class SyncPushResponseItem(val event: EventDto, val applied: Boolean)

@Serializable
data class SyncPushResponse(val server_clock: Long, val results: List<SyncPushResponseItem>)

@Serializable
data class SyncPullResponse(val server_clock: Long, val events: List<EventDto>)
