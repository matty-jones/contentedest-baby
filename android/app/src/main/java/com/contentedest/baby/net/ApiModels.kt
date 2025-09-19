package com.contentedest.baby.net

data class PairRequest(val pairing_code: String, val device_id: String, val name: String? = null)

data class PairResponse(val device_id: String, val token: String)

data class Healthz(val status: String)
