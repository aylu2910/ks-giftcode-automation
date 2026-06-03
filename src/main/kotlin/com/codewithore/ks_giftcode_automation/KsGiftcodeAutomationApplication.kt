package com.codewithore.ks_giftcode_automation

import com.codewithore.ks_giftcode_automation.config.AutomationConfig
import com.codewithore.ks_giftcode_automation.config.RetryConfig
import com.microsoft.playwright.CLI
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableConfigurationProperties(AutomationConfig::class, RetryConfig::class)
class KsGiftcodeAutomationApplication {

	private val logger = LoggerFactory.getLogger(KsGiftcodeAutomationApplication::class.java)

	@PostConstruct
	fun installPlaywright() {
		val playwrightDir = System.getProperty("user.home") + "/Library/Caches/ms-playwright"
		val chromiumExists = java.io.File(playwrightDir)
			.listFiles()
			?.any { it.name.startsWith("chromium-") } ?: false

		if (chromiumExists) {
			logger.info(">>> Playwright browsers already installed — skipping download ✅")
		} else {
			logger.info(">>> Installing Playwright browsers...")
			CLI.main(arrayOf("install", "chromium"))
			logger.info(">>> Playwright browsers installed!")
		}
	}
}

fun main(args: Array<String>) {
	runApplication<KsGiftcodeAutomationApplication>(*args)
}