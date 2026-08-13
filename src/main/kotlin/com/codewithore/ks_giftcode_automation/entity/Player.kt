package com.codewithore.ks_giftcode_automation.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "players")
data class Player(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "player_id", nullable = false, unique = true)
    val playerId: String,

    @Column(name = "player_name")
    val playerName: String? = null,

    @Column(name = "added_at", nullable = false)
    val addedAt: LocalDate = LocalDate.now()
)