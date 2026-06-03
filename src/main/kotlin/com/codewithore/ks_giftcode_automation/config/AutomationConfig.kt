package com.codewithore.ks_giftcode_automation.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "kingshot.automation")
data class AutomationConfig @ConstructorBinding constructor(
    val workers: Int,
    val timeoutMs: Long
)