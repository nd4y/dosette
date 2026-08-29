package icu.nd4y.dosette.domain.model

import java.time.Instant

/** One package form of a medication with its own stock pool. */
data class MedicationVariant(
    val id: String,
    val medicationId: String,
    val label: String?,
    val strengthValue: Double?,
    val strengthUnit: String?,
    val sortOrder: Int,
    val trackingEnabled: Boolean,
    val currentStock: Double,
    val lowStockThreshold: Double?,
    val defaultRefillAmount: Double?,
    val lastRefillAt: Instant?,
)
