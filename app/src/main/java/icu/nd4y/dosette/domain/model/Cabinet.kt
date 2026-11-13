package icu.nd4y.dosette.domain.model

import java.time.Instant

data class Cabinet(
    val id: String,
    val profileId: String,
    val name: String?,
    val sortOrder: Int,
    val createdAt: Instant,
    val archivedAt: Instant? = null,
)

data class CabinetStock(
    val variantId: String,
    val cabinetId: String,
    val currentStock: Double,
    val lastRefillAt: Instant? = null,
)

fun defaultCabinetId(profileId: String): String = "main:$profileId"
