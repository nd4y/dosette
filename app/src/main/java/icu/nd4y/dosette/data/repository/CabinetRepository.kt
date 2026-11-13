package icu.nd4y.dosette.data.repository

import icu.nd4y.dosette.data.db.dao.CabinetDao
import icu.nd4y.dosette.data.db.entity.CabinetEntity
import icu.nd4y.dosette.data.db.toDomain
import icu.nd4y.dosette.domain.model.Cabinet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CabinetState(
    val cabinets: List<Cabinet> = emptyList(),
    val selectedId: String? = null,
) {
    val selected: Cabinet? get() = cabinets.firstOrNull { it.id == selectedId }
    val active: List<Cabinet> get() = cabinets.filter { it.archivedAt == null }
}

@Singleton
class CabinetRepository
    @Inject
    constructor(
        private val dao: CabinetDao,
        private val clock: Clock,
    ) {
        fun observe(profileId: String): Flow<CabinetState> =
            flow {
                dao.ensure(profileId)
                emitAll(
                    combine(dao.observe(profileId), dao.observeSelection(profileId)) { rows, selected ->
                        CabinetState(rows.map { it.toDomain() }, selected)
                    },
                )
            }

        suspend fun getAll(profileId: String): List<Cabinet> {
            dao.ensure(profileId)
            return dao.getAll(profileId).map { it.toDomain() }
        }

        suspend fun selectedId(profileId: String): String? {
            dao.ensure(profileId)
            return dao.selection(profileId)
        }

        suspend fun create(
            profileId: String,
            name: String,
        ): String {
            dao.ensure(profileId)
            val id = UUID.randomUUID().toString()
            dao.upsert(CabinetEntity(id, profileId, validName(name), dao.getAll(profileId).size, clock.instant()))
            return id
        }

        suspend fun rename(
            profileId: String,
            id: String,
            name: String,
        ) {
            val row = requireNotNull(dao.get(id))
            require(row.profileId == profileId)
            dao.upsert(row.copy(name = validName(name)))
        }

        suspend fun select(
            profileId: String,
            id: String,
        ) = dao.select(profileId, id)

        suspend fun archive(
            profileId: String,
            id: String,
        ) = dao.archive(profileId, id, clock.instant())

        suspend fun restore(
            profileId: String,
            id: String,
        ) {
            val row = requireNotNull(dao.get(id))
            require(row.profileId == profileId)
            dao.upsert(row.copy(archivedAt = null))
        }

        private fun validName(name: String): String = name.trim().also { require(it.isNotEmpty() && it.length <= 60) }
    }
