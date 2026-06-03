package com.codewithore.ks_giftcode_automation.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "kingshot.retry")
data class RetryConfig @ConstructorBinding constructor(
    val maxAttempts: Int,
    val initialDelayMs: Long,
    val backoffMultiplier: Int
)