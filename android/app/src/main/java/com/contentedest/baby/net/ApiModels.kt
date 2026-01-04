package com.contentedest.baby.net

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PairRequest(
    @Json(name = "pairing_code") val pairingCode: String,
    @Json(name = "device_id") val deviceId: String,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class PairResponse(
    @Json(name = "device_id") val deviceId: String,
    val token: String
)

@JsonClass(generateAdapter = true)
data class Healthz(val status: String)

@JsonClass(generateAdapter = true)
data class EventDto(
    @Json(name = "event_id") val eventId: String,
    val type: String,
    val details: String? = null,  // New field for Details from CSV
    val payload: Map<String, Any>? = null,
    @Json(name = "start_ts") val startTs: Long? = null,
    @Json(name = "end_ts") val endTs: Long? = null,
    val ts: Long? = null,
    @Json(name = "created_ts") val createdTs: Long,
    @Json(name = "updated_ts") val updatedTs: Long,
    val version: Int,
    val deleted: Boolean = false,
    @Json(name = "device_id") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class SyncPushResponseItem(val event: EventDto, val applied: Boolean)

@JsonClass(generateAdapter = true)
data class SyncPushResponse(
    @Json(name = "server_clock") val serverClock: Long,
    val results: List<SyncPushResponseItem>
)

@JsonClass(generateAdapter = true)
data class SyncPullResponse(
    @Json(name = "server_clock") val serverClock: Long,
    val events: List<EventDto>
)

@JsonClass(generateAdapter = true)
data class UpdateInfoResponse(
    @Json(name = "version_code") val versionCode: Int,
    @Json(name = "version_name") val versionName: String,
    @Json(name = "download_url") val downloadUrl: String,
    @Json(name = "release_notes") val releaseNotes: String? = null,
    @Json(name = "commit_message") val commitMessage: String? = null,
    val mandatory: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GrowthDataDto(
    val id: String,
    @Json(name = "device_id") val deviceId: String,
    val category: String,  // weight, height, head
    val value: Double,
    val unit: String,
    val ts: Long,
    @Json(name = "created_ts") val createdTs: Long,
    @Json(name = "updated_ts") val updatedTs: Long,
    val version: Int,
    val deleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GrowthPushResponse(
    @Json(name = "server_clock") val serverClock: Long,
    val applied: Boolean,
    val data: GrowthDataDto
)

@JsonClass(generateAdapter = true)
data class GrowthPullResponse(
    @Json(name = "server_clock") val serverClock: Long,
    val data: List<GrowthDataDto>
)
