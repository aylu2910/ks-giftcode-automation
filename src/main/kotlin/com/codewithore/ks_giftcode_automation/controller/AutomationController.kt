package com.codewithore.ks_giftcode_automation.controller

import com.codewithore.ks_giftcode_automation.dto.ApiResponse
import com.codewithore.ks_giftcode_automation.service.RedemptionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/automation")
class AutomationController(
    private val redemptionService: RedemptionService
) {

    @PostMapping("/run")
    fun triggerRun(): ResponseEntity<ApiResponse<Nothing>> {
        redemptionService.run()
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Redemption run triggered successfully",
                data = null
            )
        )
    }
}