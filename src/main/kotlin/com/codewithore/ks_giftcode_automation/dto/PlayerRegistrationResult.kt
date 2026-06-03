package com.codewithore.ks_giftcode_automation.dto

data class PlayerRegistrationResult(
    val playerId: String,
    val success: Boolean,
    val playerName: String? = null,
    val message: String
)
