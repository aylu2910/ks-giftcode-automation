package com.codewithore.ks_giftcode_automation.model

import com.fasterxml.jackson.annotation.JsonProperty

data class PlayerInfoResponse(
    @JsonProperty("status")
    val status: String,

    @JsonProperty("data")
    val data: PlayerInfoData? = null,

    @JsonProperty("message")
    val message: String
)

data class PlayerInfoData(
    @JsonProperty("playerId")
    val playerId: String,

    @JsonProperty("name")
    val name: String,

    @JsonProperty("kingdom")
    val kingdom: Int,

    @JsonProperty("level")
    val level: Int,

    @JsonProperty("levelRendered")
    val levelRendered: String,

    @JsonProperty("profilePhoto")
    val profilePhoto: String? = null
)