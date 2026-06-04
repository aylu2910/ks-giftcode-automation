package com.codewithore.ks_giftcode_automation.repository

import com.codewithore.ks_giftcode_automation.entity.RedemptionLog
import com.codewithore.ks_giftcode_automation.model.RedemptionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface RedemptionLogRepository : JpaRepository<RedemptionLog, Long> {

    fun existsByUserIdAndCodeAndStatus(
        userId: String,
        code: String,
        status: RedemptionStatus
    ): Boolean

    fun findByUserIdAndCode(
        userId: String,
        code: String
    ): List<RedemptionLog>

    fun findByStatus(status: RedemptionStatus): List<RedemptionLog>

    fun findByUserId(userId: String): List<RedemptionLog>

    fun countByCodeAndStatus(code: String, status: RedemptionStatus): Long

    fun existsByCodeAndStatusAndAttemptedAtAfter(
        code: String,
        status: RedemptionStatus,
        since: Instant
    ): Boolean
}