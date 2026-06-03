package com.codewithore.ks_giftcode_automation.model

import com.fasterxml.jackson.annotation.JsonProperty

data class GiftCodeData(
    @JsonProperty("giftCodes")
    val giftCodes: List<GiftCode>,

    @JsonProperty("total")
    val total: Int,

    @JsonProperty("activeCount")
    val activeCount: Int,

    @JsonProperty("expiredCount")
    val expiredCount: Int
)
