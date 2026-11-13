package icu.nd4y.dosette.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import icu.nd4y.dosette.data.db.entity.CabinetEntity
import icu.nd4y.dosette.data.db.entity.CabinetSelectionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface CabinetDao {
    @Query("SELECT * FROM cabinets WHERE profileId = :profileId ORDER BY sortOrder, id")
    fun observe(profileId: String): Flow<List<CabinetEntity>>

    @Query("SELECT * FROM cabinets WHERE profileId = :profileId ORDER BY sortOrder, id")
    suspend fun getAll(profileId: String): List<CabinetEntity>

    @Query("SELECT * FROM cabinets WHERE id = :id")
    suspend fun get(id: String): CabinetEntity?

    @Query("SELECT cabinetId FROM cabinet_selections WHERE profileId = :profileId")
    fun observeSelection(profileId: String): Flow<String?>

    @Query("SELECT cabinetId FROM cabinet_selections WHERE profileId = :profileId")
    suspend fun selection(profileId: String): String?

    @Upsert suspend fun upsert(cabinet: CabinetEntity)

    @Upsert suspend fun selectRow(selection: CabinetSelectionEntity)

    @Query(
        "INSERT OR IGNORE INTO cabinets (id, profileId, name, sortOrder, createdAt, archivedAt) " +
            "SELECT 'main:' || id, id, NULL, 0, createdAt, NULL FROM profiles WHERE id = :profileId",
    )
    suspend fun ensureDefault(profileId: String)

    @Query(
        "INSERT OR IGNORE INTO cabinet_selections (profileId, cabinetId) " +
            "SELECT id, 'main:' || id FROM profiles WHERE id = :profileId",
    )
    suspend fun ensureSelection(profileId: String)

    @Query("SELECT COALESCE(SUM(currentStock), 0.0) FROM cabinet_stocks WHERE cabinetId = :id")
    suspend fun totalStock(id: String): Double

    @Transaction suspend fun ensure(profileId: String) {
        ensureDefault(profileId)
        ensureSelection(profileId)
    }

    @Transaction suspend fun select(
        profileId: String,
        id: String,
    ) {
        val cabinet = requireNotNull(get(id))
        require(cabinet.profileId == profileId && cabinet.archivedAt == null)
        selectRow(CabinetSelectionEntity(profileId, id))
    }

    @Transaction suspend fun archive(
        profileId: String,
        id: String,
        at: Instant,
    ) {
        val cabinet = requireNotNull(get(id))
        require(cabinet.profileId == profileId)
        val remaining = getAll(profileId).filter { it.id != id && it.archivedAt == null }
        require(remaining.isNotEmpty()) { "last_cabinet" }
        require(totalStock(id) == 0.0) { "cabinet_has_stock" }
        if (selection(profileId) == id) selectRow(CabinetSelectionEntity(profileId, remaining.first().id))
        upsert(cabinet.copy(archivedAt = at))
    }
}
