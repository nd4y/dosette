package icu.nd4y.dosette.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class MedicationEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val form: String,
    val strengthValue: Double?,
    val strengthUnit: String?,
    val instructions: String?,
    val colorSeed: Int,
    val iconKey: String,
    val archivedAt: Instant?,
    val createdAt: Instant,
)
