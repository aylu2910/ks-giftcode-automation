package com.codewithore.ks_giftcode_automation.service

import com.codewithore.ks_giftcode_automation.config.AutomationConfig
import com.codewithore.ks_giftcode_automation.config.RetryConfig
import com.codewithore.ks_giftcode_automation.entity.Player
import com.codewithore.ks_giftcode_automation.entity.RedemptionLog
import com.codewithore.ks_giftcode_automation.model.GiftCode
import com.codewithore.ks_giftcode_automation.model.RedemptionStatus
import com.codewithore.ks_giftcode_automation.repository.RedemptionLogRepository
import com.microsoft.playwright.Page
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class RedemptionAutomator(
    private val redemptionLogRepository: RedemptionLogRepository,
    private val retryConfig: RetryConfig,
    private val automationConfig: AutomationConfig
) {

    private val logger = LoggerFactory.getLogger(RedemptionAutomator::class.java)

    companion object {
        const val REDEMPTION_URL = "https://kingshot.net/gift-codes/redeem"
        const val SELECTOR_PLAYER_ID = "#playerId"
        const val BTN_CONTINUE = "button[type='submit']:has-text('Continue')"
        const val SELECTOR_PLAYER_CARD = "[data-slot='card-content'] p.font-medium.text-balance"
        const val SELECTOR_PLAYER_LOOKUP_ERROR = "form:has(#playerId) p.text-red-500"
        const val BTN_REDEEM_CODE = "button[type='submit']:has-text('Redeem Gift Code')"
        const val SELECTOR_GIFT_CODE = "##giftCode"
        const val SELECTOR_GIFT_CODE_RESULT = "form:has(#giftCode) p.text-red-500"
    }

    /**
     * Processes all gift codes for a single user.
     * Returns true if processing should continue for other codes,
     * false if a hard stop condition was hit (e.g. INVALID_CODE).
     */
    fun redeemAllCodesForUser(
        page: Page,
        player: Player,
        codes: List<GiftCode>
    ): Boolean {
        val playerId = player.playerId

        logger.info("Processing user: {}", player.id)

        // Step 1 — look up the player
        page.fill(SELECTOR_PLAYER_ID, playerId)
        page.click(BTN_CONTINUE)

        page.waitForSelector(
            "$SELECTOR_PLAYER_CARD, $SELECTOR_PLAYER_LOOKUP_ERROR",
            Page.WaitForSelectorOptions()
                .setTimeout(automationConfig.timeoutMs.toDouble() * 5)
        )

        // Step 2 — Redeem each code
        for (code in codes) {

            // Skip if already successfully redeemed
            if (redemptionLogRepository.existsByUserIdAndCodeAndStatus(
                    playerId, code.code, RedemptionStatus.SUCCESS
                )
            ) {
                logger.info("Skipping code {} for user {} — already redeemed based on RedemptionStatus.SUCCESS", code.code, playerId)
                continue
            }

            val hardStop = redeemCodeWithRetry(page, playerId, code)
            if (hardStop) return false
        }

        // Step 3 — Exit to reset form for next user
        return true
    }

    private fun redeemCodeWithRetry(
        page: Page,
        playerId: String,
        code: GiftCode
    ): Boolean {
        var attempt = 1
        var delayMs = retryConfig.initialDelayMs

        while (attempt <= retryConfig.maxAttempts) {
            logger.info(
                "Redeeming code {} for user {} — attempt {}/{}",
                code.code, playerId, attempt, retryConfig.maxAttempts
            )

            // Write PENDING before attempting
            val log = saveLog(playerId, code.code, RedemptionStatus.PENDING, attempt, null)

            val status = attemptRedemption(page, code.code)

            // Update log with actual result
            updateLog(log, status)

            when {
                status == RedemptionStatus.SUCCESS -> {
                    logger.info("✅ Code {} redeemed for user {} ", code.code, playerId)
                    return false
                }
                status in RedemptionStatus.HARD_STOP -> {
                    logger.warn("⛔ Hard stop for code {}: {}", code.code, status)
                    return true
                }
                status == RedemptionStatus.ALREADY_REDEEMED -> {
                    logger.info("⏭️ Code {} already redeemed for user {} ", code.code, playerId)
                    return false
                }
                status in RedemptionStatus.RETRYABLE && attempt < retryConfig.maxAttempts -> {
                    logger.warn(
                        "⚠️ Retryable error for code {} user {} — waiting {}ms",
                        code.code, playerId, delayMs
                    )
                    Thread.sleep(delayMs)
                    delayMs *= retryConfig.backoffMultiplier
                    attempt++
                }
                else -> {
                    logger.error("❌ Code {} failed for user {} after {} attempts", code.code, playerId, attempt)
                    updateLog(log, RedemptionStatus.FAILED)
                    return false
                }
            }
        }
        return false
    }

    private fun attemptRedemption(page: Page, code: String): RedemptionStatus {
        return try {
            page.fill(SELECTOR_GIFT_CODE, code)
            page.click(BTN_REDEEM_CODE)

            page.waitForSelector(
                SELECTOR_GIFT_CODE_RESULT,
                Page.WaitForSelectorOptions()
                    .setTimeout(automationConfig.timeoutMs.toDouble() * 5)
            )

            val resultText = page.innerText(SELECTOR_GIFT_CODE_RESULT)
            RedemptionStatus.fromMessage(resultText)

        } catch (e: Exception) {
            logger.error("Playwright error during redemption: {}", e.message)
            RedemptionStatus.UNKNOWN_ERROR
        }
    }

    @Transactional
    fun saveLog(
        userId: String,
        code: String,
        status: RedemptionStatus,
        attemptNumber: Int,
        errorMessage: String?
    ): RedemptionLog {
        return redemptionLogRepository.save(
            RedemptionLog(
                userId = userId,
                code = code,
                status = status,
                attemptNumber = attemptNumber,
                errorMessage = errorMessage,
                attemptedAt = Instant.now(),
                redeemedAt = if (status == RedemptionStatus.SUCCESS) Instant.now() else null
            )
        )
    }

    @Transactional
    fun updateLog(log: RedemptionLog, status: RedemptionStatus): RedemptionLog {
        return redemptionLogRepository.save(
            log.copy(
                status = status,
                redeemedAt = if (status == RedemptionStatus.SUCCESS) Instant.now() else null,
                errorMessage = if (status in RedemptionStatus.RETRYABLE || status == RedemptionStatus.FAILED)
                    status.message else null
            )
        )
    }
}