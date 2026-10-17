package icu.nd4y.dosette.link

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatchJsonTest {
    private val snapshot =
        WatchSnapshot(
            date = "2026-09-17",
            profileName = "Анна",
            doses =
                listOf(
                    WatchDose(
                        id = "m1|2026-09-17|08:00",
                        title = "Метформин 500 мг",
                        amount = "1",
                        timeMinutes = 8 * 60,
                        status = WatchDoseStatus.TAKEN,
                        actedTimeMinutes = 8 * 60 + 3,
                    ),
                    WatchDose(
                        id = "m2|2026-09-16|22:00",
                        title = "Мелатонин",
                        amount = null,
                        timeMinutes = 22 * 60,
                        status = WatchDoseStatus.PENDING,
                        carryover = true,
                        reminderActive = true,
                    ),
                ),
            prn = listOf(WatchPrn("m3", "Ибупрофен 400 мг")),
        )

    @Test
    fun `snapshot survives a round trip`() {
        val text = WatchJson.encode(snapshot)

        assertThat(WatchJson.decodeSnapshot(text)).isEqualTo(snapshot)
    }

    @Test
    fun `action survives a round trip`() {
        val action =
            WatchAction(
                id = "a1",
                kind = WatchActionKind.TAKE,
                doseId = "m1|2026-09-17|08:00",
                actedAt = 1_789_600_000_000L,
            )

        assertThat(WatchJson.decodeAction(WatchJson.encode(action))).isEqualTo(action)
    }

    @Test
    fun `equal content encodes to equal bytes`() {
        // The Data Layer deduplicates identical payloads; a field order or
        // default-encoding wobble would make every publish a sync.
        assertThat(WatchJson.encode(snapshot)).isEqualTo(WatchJson.encode(snapshot.copy()))
    }

    @Test
    fun `unknown keys from a newer counterpart are ignored`() {
        val text = """{"date":"2026-09-17","doses":[],"prn":[],"mood":"fine"}"""

        assertThat(WatchJson.decodeSnapshot(text)).isEqualTo(WatchSnapshot(date = "2026-09-17"))
    }

    @Test
    fun `malformed documents decode to null`() {
        assertThat(WatchJson.decodeSnapshot("not json")).isNull()
        assertThat(WatchJson.decodeSnapshot("""{"doses":[]}""")).isNull()
        assertThat(WatchJson.decodeAction("""{"id":"a1","kind":"DANCE","actedAt":1}""")).isNull()
    }

    @Test
    fun `action paths carry the action id`() {
        assertThat(LinkPaths.actionPath("a1")).isEqualTo("/dosette/action/a1")
        assertThat(LinkPaths.actionPath("a1")).startsWith(LinkPaths.PREFIX)
        assertThat(LinkPaths.TODAY).startsWith(LinkPaths.PREFIX)
    }

    @Test
    fun `invalid snapshot times dates versions and duplicate ids are rejected`() {
        val dose = snapshot.doses.first()
        val invalid =
            listOf(
                snapshot.copy(schema = 2),
                snapshot.copy(date = "not-a-date"),
                snapshot.copy(doses = listOf(dose.copy(timeMinutes = -1))),
                snapshot.copy(doses = listOf(dose.copy(timeMinutes = 24 * 60))),
                snapshot.copy(doses = listOf(dose.copy(actedTimeMinutes = 24 * 60))),
                snapshot.copy(doses = listOf(dose, dose)),
                snapshot.copy(prn = listOf(WatchPrn("", "Unknown"))),
                snapshot.copy(prn = snapshot.prn + snapshot.prn),
            )
        invalid.forEach { assertThat(WatchJson.decodeSnapshot(WatchJson.encode(it))).isNull() }
    }

    @Test
    fun `invalid action identity targets and negative timestamps are rejected`() {
        val action = WatchAction("a1", WatchActionKind.TAKE, doseId = "dose", actedAt = 1)
        val invalid =
            listOf(
                action.copy(id = "../other"),
                action.copy(id = ""),
                action.copy(actedAt = -1),
                action.copy(doseId = null),
                action.copy(medicationId = "m1"),
                action.copy(kind = WatchActionKind.TAKE_PRN),
            )
        invalid.forEach { assertThat(WatchJson.decodeAction(WatchJson.encode(it))).isNull() }
        assertThat(WatchJson.decodeSnapshot(" ".repeat(100_001))).isNull()
    }
}
