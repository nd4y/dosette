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
}
