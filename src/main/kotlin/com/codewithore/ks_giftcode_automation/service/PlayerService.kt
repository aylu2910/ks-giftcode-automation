package com.codewithore.ks_giftcode_automation.service

import com.codewithore.ks_giftcode_automation.entity.Player
import com.codewithore.ks_giftcode_automation.repository.PlayerRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PlayerService(
    private val playerRepository: PlayerRepository
) {

    private val logger = LoggerFactory.getLogger(PlayerService::class.java)

    @Cacheable("players")
    fun getAllPlayers(): List<Player> {
        logger.info("Loading players from database...")
        val players = playerRepository.findAll()
        logger.info("Loaded {} players from database", players.size)
        return players
    }

    fun loadUserIds(): List<String> =
        getAllPlayers().map { it.playerId }

    @CacheEvict("players", allEntries = true)
    fun addPlayer(playerId: String, playerName: String?, kingdom: String?): Player {
        if (playerRepository.existsByPlayerId(playerId)) {
            logger.warn("Player {} already exists in database", playerId)
            throw IllegalArgumentException("Player ID $playerId is already registered")
        }
        val player = Player(
            playerId = playerId,
            playerName = playerName,
            kingdom = kingdom,
            kingdomUpdatedAt = if (kingdom != null) LocalDate.now() else null
        )
        return playerRepository.save(player).also {
            logger.info("Player {} ({}) from kingdom {} added on {}", playerId, playerName, kingdom, it.addedAt)
        }
    }

    @CacheEvict("players", allEntries = true)
    fun updatePlayerKingdom(playerId: String, kingdom: String): Player {
        val player = playerRepository.findByPlayerId(playerId)
            ?: throw IllegalArgumentException("Player ID $playerId not found")

        val updatedPlayer = player.copy(
            kingdom = kingdom,
            kingdomUpdatedAt = LocalDate.now()
        )
        return playerRepository.save(updatedPlayer).also {
            logger.info("Player {} kingdom updated to {}", playerId, kingdom)
        }
    }

    @CacheEvict("players", allEntries = true)
    fun removePlayer(playerId: String) {
        val player = playerRepository.findByPlayerId(playerId)
            ?: throw IllegalArgumentException("Player ID $playerId not found")
        playerRepository.delete(player)
        logger.info("Player {} removed from database", playerId)
    }
}