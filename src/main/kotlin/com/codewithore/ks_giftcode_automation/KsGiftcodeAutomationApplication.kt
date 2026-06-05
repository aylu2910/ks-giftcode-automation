package com.codewithore.ks_giftcode_automation

import com.codewithore.ks_giftcode_automation.config.AutomationConfig
import com.codewithore.ks_giftcode_automation.config.RetryConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableConfigurationProperties(AutomationConfig::class, RetryConfig::class)
class KsGiftcodeAutomationApplication

fun main(args: Array<String>) {
	runApplication<KsGiftcodeAutomationApplication>(*args)
}