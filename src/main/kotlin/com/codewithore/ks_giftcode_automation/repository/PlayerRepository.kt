package com.codewithore.ks_giftcode_automation.repository

import com.codewithore.ks_giftcode_automation.entity.Player
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PlayerRepository : JpaRepository<Player, Long> {

    fun existsByPlayerId(playerId: String): Boolean

    fun findByPlayerId(playerId: String): Player?
}