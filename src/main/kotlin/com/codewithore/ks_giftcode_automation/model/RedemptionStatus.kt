package com.codewithore.ks_giftcode_automation.model

enum class RedemptionStatus(val message: String) {
    PENDING("Redemption in progress."),
    SUCCESS("Redeemed, please claim the rewards in your mail!"),
    ALREADY_REDEEMED("Gift has already been claimed!"),
    INVALID_CODE("Gift Code not found, this is case-sensitive!"),
    EXPIRED("This gift code has expired."),
    CLAIM_LIMIT_REACHED("Claim limit reached, unable to claim."),
    INVALID_PLAYER("Player ID not found!"),
    SERVER_BUSY("Server busy. Please try again later."),
    UNKNOWN_ERROR("Unknown error occurred, will retry."),
    FAILED("All retry attempts exhausted.");

    companion object {
        private const val ALREADY_REDEEMED_ALT = "The same Gift Code type can only be redeemed once!"

        val RETRYABLE = setOf(SERVER_BUSY, UNKNOWN_ERROR)
        val HARD_STOP = setOf(INVALID_CODE, EXPIRED, CLAIM_LIMIT_REACHED)

        fun fromMessage(popupText: String): RedemptionStatus {
            if (popupText.contains(ALREADY_REDEEMED_ALT, ignoreCase = true)) {
                return ALREADY_REDEEMED
            }
            return entries.find {
                popupText.contains(it.message, ignoreCase = true)
            } ?: UNKNOWN_ERROR
        }
    }
}