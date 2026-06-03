package com.codewithore.ks_giftcode_automation.client

import com.codewithore.ks_giftcode_automation.model.GiftCode
import com.codewithore.ks_giftcode_automation.model.GiftCodeResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class GiftCodeAPIClient(
    private val restClient: RestClient = RestClient.create()
) {

    private val logger = LoggerFactory.getLogger(GiftCodeAPIClient::class.java)

    @Value("\${kingshot.api.gift-codes-url}")
    private lateinit var giftCodesUrl: String

    fun fetchActiveCodes(): List<GiftCode> {
        return try {
            logger.info("Fetching gift codes from API...")

            val response = restClient.get()
                .uri(giftCodesUrl)
                .retrieve()
                .body(GiftCodeResponse::class.java)

            if (response == null || response.status != "success") {
                logger.warn("Gift code API returned unsuccessful response")
                return emptyList()
            }

            logger.info("Fetched {} active gift codes", response.data.activeCount)

            response.data.giftCodes

        } catch (e: RestClientException) {
            logger.error("Failed to fetch gift codes from API: {}", e.message)
            emptyList()
        }
    }
}