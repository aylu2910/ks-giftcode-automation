package com.codewithore.ks_giftcode_automation.client

import com.codewithore.ks_giftcode_automation.model.PlayerInfoData
import com.codewithore.ks_giftcode_automation.model.PlayerInfoResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

@Component
class PlayerInfoApiClient(
    private val restClient: RestClient = RestClient.create()
) {

    private val logger = LoggerFactory.getLogger(PlayerInfoApiClient::class.java)

    @Value("\${kingshot.api.player-info-url}")
    private lateinit var playerInfoUrl: String

    fun getPlayerInfo(playerId: String): PlayerInfoData? {
        return try {
            logger.info("Validating player ID: {}", playerId)

            val response = restClient.get()
                .uri("$playerInfoUrl?playerId=$playerId")
                .retrieve()
                .body(PlayerInfoResponse::class.java)

            if (response?.status == "success" && response.data != null) {
                logger.info("Player {} found: {}", playerId, response.data.name)
                response.data
            } else {
                logger.warn("Player {} not found", playerId)
                null
            }

        } catch (e: HttpClientErrorException.TooManyRequests) {
            logger.warn("Rate limited while validating player {}", playerId)
            throw RateLimitException("Too many requests — please try again later")
        } catch (e: HttpClientErrorException) {
            logger.warn("Player {} not found: {}", playerId, e.message)
            null
        } catch (e: Exception) {
            logger.error("Error validating player {}: {}", playerId, e.message)
            null
        }
    }
}

class RateLimitException(message: String) : RuntimeException(message)