/**
 * Time-Expiring Authorization Code Handler
 * 
 * This module implements a time-expiring authorization code mechanism for the RCS-System.
 * Authorization codes generated in response to "LOC" (Location Request) commands are valid
 * for only a configurable time window (default: 60 seconds), after which they automatically expire.
 * 
 * Novel Security Mechanism:
 * - Prevents code reuse attacks through temporal validation
 * - Reduces exposure window for compromised codes
 * - Forces users to request new authorization if timeout occurs
 */

package com.rcs.security

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class AuthorizationCodeConfig(
    val expirationWindowSeconds: Long = 60L,
    val cleanupIntervalSeconds: Long = 30L
)

data class AuthorizationCodeEntry(
    val code: String,
    val userId: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val locationRequest: LocationRequestData,
    var isRedeemed: Boolean = false
)

data class LocationRequestData(
    val requestId: String,
    val requestedBy: String,
    val reason: String,
    val requestedAt: Instant
)

class TimeExpiringAuthorizationCodeManager(
    private val config: AuthorizationCodeConfig = AuthorizationCodeConfig()
) {
    private val authorizationCodes = ConcurrentHashMap<String, AuthorizationCodeEntry>()
    private val codeIndex = ConcurrentHashMap<String, String>()
    
    fun generateAuthorizationCode(
        userId: String,
        locationRequest: LocationRequestData
    ): String {
        val code = generateUniqueCode()
        val now = Instant.now()
        val expiresAt = now.plusSeconds(config.expirationWindowSeconds)
        
        val entry = AuthorizationCodeEntry(
            code = code,
            userId = userId,
            issuedAt = now,
            expiresAt = expiresAt,
            locationRequest = locationRequest
        )
        
        authorizationCodes[code] = entry
        codeIndex[userId] = code
        
        return code
    }
    
    fun validateAuthorizationCode(code: String): ValidationResult {
        val entry = authorizationCodes[code] ?: return ValidationResult(
            isValid = false,
            reason = "CODE_NOT_FOUND"
        )
        
        if (isCodeExpired(entry)) {
            authorizationCodes.remove(code)
            return ValidationResult(
                isValid = false,
                reason = "CODE_EXPIRED",
                expirationTime = entry.expiresAt
            )
        }
        
        if (entry.isRedeemed) {
            return ValidationResult(
                isValid = false,
                reason = "CODE_ALREADY_REDEEMED"
            )
        }
        
        return ValidationResult(
            isValid = true,
            entry = entry
        )
    }
    
    fun redeemAuthorizationCode(code: String): Boolean {
        val entry = authorizationCodes[code] ?: return false
        
        if (isCodeExpired(entry) || entry.isRedeemed) {
            return false
        }
        
        entry.isRedeemed = true
        return true
    }
    
    private fun isCodeExpired(entry: AuthorizationCodeEntry): Boolean {
        return Instant.now().isAfter(entry.expiresAt)
    }
    
    private fun generateUniqueCode(): String {
        var code: String
        do {
            code = generateRandomAlphanumeric(32)
        } while (authorizationCodes.containsKey(code))
        return code
    }
    
    private fun generateRandomAlphanumeric(length: Int): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { charset.random() }
            .joinToString("")
    }
    
    fun cleanupExpiredCodes() {
        val expiredCodes = authorizationCodes.entries
            .filter { isCodeExpired(it.value) }
            .map { it.key }
        
        expiredCodes.forEach { authorizationCodes.remove(it) }
    }
    
    fun getRemainingTimeSeconds(code: String): Long? {
        val entry = authorizationCodes[code] ?: return null
        val remaining = entry.expiresAt.epochSecond - Instant.now().epochSecond
        return if (remaining > 0) remaining else null
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val reason: String? = null,
    val expirationTime: Instant? = null,
    val entry: AuthorizationCodeEntry? = null
)