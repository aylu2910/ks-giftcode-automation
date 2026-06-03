package com.codewithore.ks_giftcode_automation.service

import com.codewithore.ks_giftcode_automation.config.AutomationConfig
import com.codewithore.ks_giftcode_automation.config.RetryConfig
import com.codewithore.ks_giftcode_automation.entity.RedemptionLog
import com.codewithore.ks_giftcode_automation.model.GiftCode
import com.codewithore.ks_giftcode_automation.model.RedemptionStatus
import com.codewithore.ks_giftcode_automation.repository.RedemptionLogRepository
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.WaitForSelectorState
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
        const val REDEMPTION_URL = "https://ks-giftcode.centurygame.com/"
        const val SELECTOR_PLAYER_ID = "input[placeholder='Player ID']"
        const val SELECTOR_LOGIN_BTN = "div.btn.login_btn"
        const val SELECTOR_LOGIN_BTN_ACTIVE = "div.btn.login_btn:not(.disabled)"
        const val SELECTOR_GIFT_CODE = "input[placeholder='Enter Gift Code']"
        const val SELECTOR_EXCHANGE_BTN = "div.btn.exchange_btn"
        const val SELECTOR_MODAL = "div.message_modal"
        const val SELECTOR_MODAL_MSG = "div.modal_content .msg"
        const val SELECTOR_CONFIRM_BTN = "div.confirm_btn"
        const val SELECTOR_EXIT = "div.exit_con"
        const val SELECTOR_PLAYER_NAME = "p.name"
    }

    /**
     * Processes all gift codes for a single user.
     * Returns true if processing should continue for other codes,
     * false if a hard stop condition was hit (e.g. INVALID_CODE).
     */
    fun redeemAllCodesForUser(
        page: Page,
        userId: String,
        codes: List<GiftCode>
    ): Boolean {
        logger.info("Processing user: {}", userId)

        // Step 1 — Login
        val playerName = loginUser(page, userId) ?: return true

        // Step 2 — Redeem each code
        for (code in codes) {

            // Skip if already successfully redeemed
            if (redemptionLogRepository.existsByUserIdAndCodeAndStatus(
                    userId, code.code, RedemptionStatus.SUCCESS
                )
            ) {
                logger.info("Skipping code {} for user {} — already redeemed", code.code, userId)
                continue
            }

            val hardStop = redeemCodeWithRetry(page, userId, playerName, code)
            if (hardStop) return false
        }

        // Step 3 — Exit to reset form for next user
        exitUser(page)
        return true
    }

    private fun loginUser(page: Page, userId: String): String? {
        return try {
            page.fill(SELECTOR_PLAYER_ID, userId)

            // Wait for login button to become active (disabled class removed)
            page.waitForSelector(
                SELECTOR_LOGIN_BTN_ACTIVE,
                Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(automationConfig.timeoutMs.toDouble())
            )
            page.click(SELECTOR_LOGIN_BTN)

            // Wait for either player name (success) or modal (error)
            page.waitForSelector(
                "$SELECTOR_PLAYER_NAME, $SELECTOR_MODAL",
                Page.WaitForSelectorOptions()
                    .setTimeout(automationConfig.timeoutMs.toDouble() * 5)
            )

            // Check if player name appeared (successful login)
            val playerNameElement = page.querySelector(SELECTOR_PLAYER_NAME)
            if (playerNameElement != null && playerNameElement.isVisible) {
                val playerName = playerNameElement.innerText()
                logger.info("Logged in as: {} ({})", playerName, userId)
                return playerName
            }

            // Otherwise check for error modal
            val modalText = page.innerText(SELECTOR_MODAL_MSG)
            val status = RedemptionStatus.fromMessage(modalText)

            if (status in RedemptionStatus.SKIP_USER) {
                logger.warn("Login failed for user {}: {}", userId, modalText)
                saveLog(userId, null, "LOGIN", status, 1, modalText)
                page.click(SELECTOR_CONFIRM_BTN)
                return null
            }

            // Unexpected state — use userId as fallback
            val playerName = userId

            logger.info("Logged in as: {} ({})", playerName, userId)
            playerName

        } catch (e: Exception) {
            logger.error("Unexpected error during login for user {}: {}", userId, e.message)
            null
        }
    }

    private fun redeemCodeWithRetry(
        page: Page,
        userId: String,
        playerName: String,
        code: GiftCode
    ): Boolean {
        var attempt = 1
        var delayMs = retryConfig.initialDelayMs

        while (attempt <= retryConfig.maxAttempts) {
            logger.info(
                "Redeeming code {} for user {} — attempt {}/{}",
                code.code, userId, attempt, retryConfig.maxAttempts
            )

            // Write PENDING before attempting
            val log = saveLog(userId, playerName, code.code, RedemptionStatus.PENDING, attempt, null)

            val status = attemptRedemption(page, code.code)

            // Update log with actual result
            updateLog(log, status)

            when {
                status == RedemptionStatus.SUCCESS -> {
                    logger.info("✅ Code {} redeemed for user {}", code.code, userId)
                    return false
                }
                status in RedemptionStatus.HARD_STOP -> {
                    logger.warn("⛔ Hard stop for code {}: {}", code.code, status)
                    return true
                }
                status == RedemptionStatus.ALREADY_REDEEMED -> {
                    logger.info("⏭️ Code {} already redeemed for user {}", code.code, userId)
                    return false
                }
                status in RedemptionStatus.RETRYABLE && attempt < retryConfig.maxAttempts -> {
                    logger.warn(
                        "⚠️ Retryable error for code {} user {} — waiting {}ms",
                        code.code, userId, delayMs
                    )
                    Thread.sleep(delayMs)
                    delayMs *= retryConfig.backoffMultiplier
                    attempt++
                }
                else -> {
                    logger.error("❌ Code {} failed for user {} after {} attempts", code.code, userId, attempt)
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
            page.click(SELECTOR_EXCHANGE_BTN)

            page.waitForSelector(
                SELECTOR_MODAL,
                Page.WaitForSelectorOptions()
                    .setTimeout(automationConfig.timeoutMs.toDouble() * 5)
            )

            val modalText = page.innerText(SELECTOR_MODAL_MSG)
            val status = RedemptionStatus.fromMessage(modalText)

            page.click(SELECTOR_CONFIRM_BTN)
            status

        } catch (e: Exception) {
            logger.error("Playwright error during redemption: {}", e.message)
            RedemptionStatus.UNKNOWN_ERROR
        }
    }

    private fun exitUser(page: Page) {
        try {
            page.click(SELECTOR_EXIT)
        } catch (e: Exception) {
            logger.warn("Could not click exit button — navigating to URL instead")
            page.navigate(REDEMPTION_URL)
        }
    }

    @Transactional
    fun saveLog(
        userId: String,
        playerName: String?,
        code: String,
        status: RedemptionStatus,
        attemptNumber: Int,
        errorMessage: String?
    ): RedemptionLog {
        return redemptionLogRepository.save(
            RedemptionLog(
                userId = userId,
                player = playerName,
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
    fun validatePlayer(userId: String): String? {

        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch()
            val page = browser.newPage()

            return try {
                page.navigate(REDEMPTION_URL)
                page.fill(SELECTOR_PLAYER_ID, userId)

                page.waitForSelector(
                    SELECTOR_LOGIN_BTN_ACTIVE,
                    Page.WaitForSelectorOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(automationConfig.timeoutMs.toDouble())
                )
                page.click(SELECTOR_LOGIN_BTN)

                // Wait for either player name (success) or modal (error)
                page.waitForSelector(
                    "$SELECTOR_PLAYER_NAME, $SELECTOR_MODAL",
                    Page.WaitForSelectorOptions()
                        .setTimeout(automationConfig.timeoutMs.toDouble() * 5)
                )

                // Check if player name appeared (successful login)
                val playerNameElement = page.querySelector(SELECTOR_PLAYER_NAME)
                if (playerNameElement != null && playerNameElement.isVisible) {
                    playerNameElement.innerText()
                } else {
                    // Check for error modal
                    val modalText = page.innerText(SELECTOR_MODAL_MSG)
                    val status = RedemptionStatus.fromMessage(modalText)

                    if (status in RedemptionStatus.SKIP_USER) {
                        logger.warn("Player {} not found in KS", userId)
                        page.click(SELECTOR_CONFIRM_BTN)
                    }
                    null // signals invalid player
                }

            } catch (e: Exception) {
                logger.error("Error validating player {}: {}", userId, e.message)
                null
            } finally {
                browser.close()
            }
        }
    }
}