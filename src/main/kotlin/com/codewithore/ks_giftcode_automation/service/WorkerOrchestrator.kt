package com.codewithore.ks_giftcode_automation.service

import com.codewithore.ks_giftcode_automation.config.AutomationConfig
import com.codewithore.ks_giftcode_automation.entity.Player
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

    suspend fun orchestrate(players: List<Player>, codes: List<GiftCode>) {
        if (players.isEmpty()) {
            logger.warn("No players to process — skipping orchestration")
            return
        }

        if (codes.isEmpty()) {
            logger.warn("No gift codes to process — skipping orchestration")
            return
        }

        // Hard stop flag — shared across all workers
        // If any worker hits INVALID_CODE, all workers stop processing that code
        val hardStop = AtomicBoolean(false)

        // Split players into chunks — one chunk per worker
        val chunkSize = maxOf(1, players.size / automationConfig.workers)
        val chunks = players.chunked(chunkSize)

        logger.info(
            "Starting orchestration — {} players, {} codes, {} workers",
            players.size, codes.size, chunks.size
        )

        supervisorScope {
            chunks.mapIndexed { index, chunk ->
                async(Dispatchers.IO) {
                    runWorker(
                        workerId = index + 1,
                        players = chunk,
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
        players: List<Player>,
        codes: List<GiftCode>,
        hardStop: AtomicBoolean
    ) {
        logger.info("Worker {} starting — {} players to process", workerId, players.size)

        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch()

            try {
                val page = browser.newPage()
                page.navigate(RedemptionAutomator.REDEMPTION_URL)

                for (player in players) {
                    if (hardStop.get()) {
                        logger.warn("Worker {} — hard stop signal received, skipping player {}", workerId, player.playerId)
                        continue
                    }

                    val shouldContinue = redemptionAutomator.redeemAllCodesForUser(
                        page = page,
                        player = player,
                        codes = codes
                    )

                    if (!shouldContinue) {
                        logger.warn("Worker {} — hard stop triggered by player {}", workerId, player.playerId)
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