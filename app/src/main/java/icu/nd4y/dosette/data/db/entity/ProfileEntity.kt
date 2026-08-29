package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorSeed: Int,
    val avatarKey: String?,
    val sortOrder: Int,
    val createdAt: Instant,
)
