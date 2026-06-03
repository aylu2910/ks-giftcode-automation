package com.codewithore.ks_giftcode_automation.model

import com.fasterxml.jackson.annotation.JsonProperty

data class GiftCodeResponse(
    @JsonProperty("status")
    val status: String,

    @JsonProperty("data")
    val data: GiftCodeData,

    @JsonProperty("message")
    val message: String,

    @JsonProperty("timestamp")
    val timestamp: String
)