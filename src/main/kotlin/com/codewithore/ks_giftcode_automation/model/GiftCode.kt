package com.codewithore.ks_giftcode_automation.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class GiftCode(
    @JsonProperty("id")
    val id: Long,

    @JsonProperty("code")
    val code: String,

    @JsonProperty("expiresAt")
    val expiresAt: Instant?,

    @JsonProperty("createdAt")
    val createdAt: Instant
)