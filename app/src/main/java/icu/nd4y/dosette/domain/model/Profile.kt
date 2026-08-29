package icu.nd4y.dosette.domain.model

import java.time.Instant

data class Profile(
    val id: String,
    val name: String,
    val colorSeed: Int,
    val avatarKey: String?,
    val sortOrder: Int,
    val createdAt: Instant,
)
