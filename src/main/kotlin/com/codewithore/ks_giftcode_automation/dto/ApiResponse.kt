package com.codewithore.ks_giftcode_automation.dto

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?
)