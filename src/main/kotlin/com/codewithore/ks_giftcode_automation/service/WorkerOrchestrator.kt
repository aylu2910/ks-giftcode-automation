package com.codewithore.ks_giftcode_automation.service

import com.codewithore.ks_giftcode_automation.config.AutomationConfig
import com.codewithore.ks_giftcode_automation.model.GiftCode
import com.microsoft.playwright.Playwright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class WorkerOrchestrator(
    private val automationConfig: AutomationConfig,
    private val redemptionAutomator: RedemptionAutomator
) {

    private val logger = LoggerFactory.getLogger(WorkerOrchestrator::class.java)

    suspend fun orchestrate(userIds: List<String>, codes: List<GiftCode>) {
        if (userIds.isEmpty()) {
            logger.warn("No users to process — skipping orchestration")
            return
        }

        if (codes.isEmpty()) {
            logger.warn("No gift codes to process — skipping orchestration")
            return
        }

        // Hard stop flag — shared across all workers
        // If any worker hits INVALID_CODE, all workers stop processing that code
        val hardStop = AtomicBoolean(false)

        // Split users into chunks — one chunk per worker
        val chunkSize = maxOf(1, userIds.size / automationConfig.workers)
        val chunks = userIds.chunked(chunkSize)

        logger.info(
            "Starting orchestration — {} users, {} codes, {} workers",
            userIds.size, codes.size, chunks.size
        )

        supervisorScope {
            chunks.mapIndexed { index, chunk ->
                async(Dispatchers.IO) {
                    runWorker(
                        workerId = index + 1,
                        userIds = chunk,
                        codes = codes,
                        hardStop = hardStop
                    )
                }
            }.awaitAll()
        }

        logger.info("Orchestration complete")
    }

    private fun runWorker(
        workerId: Int,
        userIds: List<String>,
        codes: List<GiftCode>,
        hardStop: AtomicBoolean
    ) {
        logger.info("Worker {} starting — {} users to process", workerId, userIds.size)

        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch()

            try {
                val page = browser.newPage()
                page.navigate(RedemptionAutomator.REDEMPTION_URL)

                for (userId in userIds) {
                    if (hardStop.get()) {
                        logger.warn("Worker {} — hard stop signal received, skipping user {}", workerId, userId)
                        continue
                    }

                    val shouldContinue = redemptionAutomator.redeemAllCodesForUser(
                        page = page,
                        userId = userId,
                        codes = codes
                    )

                    if (!shouldContinue) {
                        logger.warn("Worker {} — hard stop triggered by user {}", workerId, userId)
                        hardStop.set(true)
                    }
                }

            } catch (e: Exception) {
                logger.error("Worker {} crashed: {}", workerId, e.message)
            } finally {
                browser.close()
                logger.info("Worker {} finished", workerId)
            }
        }
    }
}