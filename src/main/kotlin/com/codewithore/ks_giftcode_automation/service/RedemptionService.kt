package com.codewithore.ks_giftcode_automation.service

import com.codewithore.ks_giftcode_automation.client.GiftCodeAPIClient
import com.codewithore.ks_giftcode_automation.model.RedemptionStatus
import com.codewithore.ks_giftcode_automation.repository.RedemptionLogRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Service
class RedemptionService(
    private val giftCodeApiClient: GiftCodeAPIClient,
    private val playerService: PlayerService,
    private val workerOrchestrator: WorkerOrchestrator,
    private val redemptionLogRepository: RedemptionLogRepository
) {

    private val logger = LoggerFactory.getLogger(RedemptionService::class.java)
    private val isRunning = AtomicBoolean(false)

    @Scheduled(cron = "\${kingshot.scheduler.cron}")
    fun run() {

        // Step 1 — Check if already running
        if (!isRunning.compareAndSet(false, true)) {
            logger.warn("Previous run still in progress — skipping this cycle")
            return
        }

        val startTime = Instant.now()
        logger.info("========== Redemption run started ==========")

        try {
            // Step 2 — Fetch active gift codes
            val codes = giftCodeApiClient.fetchActiveCodes()
            if (codes.isEmpty()) {
                logger.info("No active gift codes found — skipping run")
                return
            }
            logger.info("Found {} gift codes to process", codes.size)

            // Step 3 — Load users
            val userIds = playerService.loadUserIds()
            if (userIds.isEmpty()) {
                logger.info("No users registered — skipping run")
                return
            }
            logger.info("Found {} users to process", userIds.size)

            // Step 4 — Filter codes already redeemed by all users
            val pendingCodes = codes.filter { code ->
                val successCount = redemptionLogRepository
                    .countByCodeAndStatus(code.code, RedemptionStatus.SUCCESS)
                successCount < playerService.getAllPlayers().size
            }

            if (pendingCodes.isEmpty()) {
                logger.info("All codes already redeemed for all users — skipping run")
                return
            }
            logger.info("{} codes still pending redemption", pendingCodes.size)

            // Step 5 — Orchestrate workers
            runBlocking {
                workerOrchestrator.orchestrate(userIds, pendingCodes)
            }

        } catch (e: Exception) {
            logger.error("Redemption run failed with error: {}", e.message)
        } finally {
            val duration = Duration.between(startTime, Instant.now())
            logger.info(
                "========== Redemption run finished in {}m {}s ==========",
                duration.toMinutes(),
                duration.toSecondsPart()
            )
            isRunning.set(false)
        }
    }
}