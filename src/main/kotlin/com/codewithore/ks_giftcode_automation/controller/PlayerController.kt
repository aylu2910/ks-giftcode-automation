package com.codewithore.ks_giftcode_automation.controller

import com.codewithore.ks_giftcode_automation.dto.AddPlayersRequest
import com.codewithore.ks_giftcode_automation.dto.ApiResponse
import com.codewithore.ks_giftcode_automation.dto.PlayerRegistrationResult
import com.codewithore.ks_giftcode_automation.repository.PlayerRepository
import com.codewithore.ks_giftcode_automation.service.PlayerService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/players")
class PlayerController(
    private val playerService: PlayerService,
    private val playerRepository: PlayerRepository,
) {

    @GetMapping
    fun getAllPlayers(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val players = playerService.getAllPlayers()
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Found ${players.size} players",
                data = mapOf(
                    "total" to players.size,
                    "players" to players
                )
            )
        )
    }

    @PostMapping
    fun addPlayers(
        @RequestBody request: AddPlayersRequest
    ): ResponseEntity<ApiResponse<List<PlayerRegistrationResult>>> {

        val results = request.playerIds.map { playerId ->
            val playerIdStr = playerId.toString()

            // Check DB first — fast path
            if (playerRepository.existsByPlayerId(playerIdStr)) {
                return@map PlayerRegistrationResult(
                    playerId = playerIdStr,
                    success = false,
                    message = "Player ID $playerId is already registered"
                )
            }

            // Save to DB with in-game name and kingdom
            runCatching { playerService.addPlayer(playerIdStr) }
                .fold(
                    onSuccess = {
                        PlayerRegistrationResult(
                            playerId = playerIdStr,
                            success = true,
                            message = "Player $playerId added successfully"
                        )
                    },
                    onFailure = {
                        PlayerRegistrationResult(
                            playerId = playerIdStr,
                            playerKingdom = null,
                            success = false,
                            message = it.message ?: "Unknown error"
                        )
                    }
                )
        }

        val allSucceeded = results.all { it.success }
        val anySucceeded = results.any { it.success }

        val status = when {
            allSucceeded -> HttpStatus.CREATED
            anySucceeded -> HttpStatus.MULTI_STATUS
            else -> HttpStatus.BAD_REQUEST
        }

        return ResponseEntity.status(status).body(
            ApiResponse(
                success = anySucceeded,
                message = "${results.count { it.success }}/${results.size} players added successfully",
                data = results
            )
        )
    }



    @DeleteMapping("/{playerId}")
    fun removePlayer(@PathVariable playerId: String): ResponseEntity<ApiResponse<Nothing>> {
        playerService.removePlayer(playerId)
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Player $playerId removed successfully",
                data = null
            )
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        val status = if (ex.message?.contains("already registered") == true)
            HttpStatus.CONFLICT else HttpStatus.NOT_FOUND
        return ResponseEntity.status(status).body(
            ApiResponse(
                success = false,
                message = ex.message ?: "Unknown error",
                data = null
            )
        )
    }
}
