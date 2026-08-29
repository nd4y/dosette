package icu.nd4y.dosette.domain.model

import java.time.Instant

data class Inventory(
    val medicationId: String,
    val trackingEnabled: Boolean,
    /** Double: half pills are a real use case. */
    val currentStock: Double,
    val lowStockThreshold: Double?,
    val defaultRefillAmount: Double?,
    val lastRefillAt: Instant?,
)
