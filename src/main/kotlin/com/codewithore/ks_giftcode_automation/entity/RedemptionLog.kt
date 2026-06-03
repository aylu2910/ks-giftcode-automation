package com.codewithore.ks_giftcode_automation.entity

import com.codewithore.ks_giftcode_automation.model.RedemptionStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "redemption_log")
data class RedemptionLog(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "player")
    val player: String? = null,

    @Column(name = "code", nullable = false)
    val code: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: RedemptionStatus,

    @Column(name = "attempt_number", nullable = false)
    val attemptNumber: Int = 1,

    @Column(name = "error_message")
    val errorMessage: String? = null,

    @Column(name = "attempted_at", nullable = false)
    val attemptedAt: Instant = Instant.now(),

    @Column(name = "redeemed_at")
    val redeemedAt: Instant? = null
)