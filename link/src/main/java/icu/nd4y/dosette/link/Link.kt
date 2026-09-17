package icu.nd4y.dosette.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Wearable Data Layer paths the phone and the watch agree on. Every item
 * carries one JSON document under [KEY_JSON]; the classes below are that
 * document. Both apps ship from the same tag, so the format only ever
 * grows — unknown keys are ignored on both sides.
 */
object LinkPaths {
    /** Common prefix, also the manifest filter of both listener services. */
    const val PREFIX = "/dosette"

    /** Phone → watch: the phone's picture of the day, one item per phone. */
    const val TODAY = "/dosette/today"

    /** Watch → phone: one item per action, deleted by the phone once applied. */
    const val ACTIONS = "/dosette/action/"

    /** Capability the phone app advertises, so the watch can tell "no phone" from "phone without Dosette". */
    const val PHONE_CAPABILITY = "dosette_phone"

    const val KEY_JSON = "json"

    fun actionPath(actionId: String): String = ACTIONS + actionId
}

@Serializable
enum class WatchDoseStatus { PENDING, TAKEN, SKIPPED, MISSED }

/** One scheduled dose as the watch shows it; the phone does all the naming. */
@Serializable
data class WatchDose(
    /** Opaque occurrence id the watch hands back in a [WatchAction]. */
    val id: String,
    /** "Metformin 500 mg" */
    val title: String,
    /** Units of the dose as display text, e.g. "1" or "2.5"; null = unknown. */
    val amount: String? = null,
    /** Minutes since midnight of the planned time. */
    val timeMinutes: Int,
    val status: WatchDoseStatus,
    /** Minutes since midnight of the mark, for taken and skipped doses. */
    val actedTimeMinutes: Int? = null,
    /** Yesterday's dose still unresolved across midnight — listed first, not counted. */
    val carryover: Boolean = false,
    /** A reminder is ringing for it right now — the only time a snooze means anything. */
    val reminderActive: Boolean = false,
)

/** An as-needed medication the watch can record an intake for. */
@Serializable
data class WatchPrn(
    val medicationId: String,
    val title: String,
)

/** The day as the phone sees it for the active profile: the same picture as the widget. */
@Serializable
data class WatchSnapshot(
    /** Bumped when a field changes meaning; the watch ignores fields it does not know. */
    val schema: Int = 1,
    /** ISO date the list is for. */
    val date: String,
    /** Shown only when more than one profile exists. */
    val profileName: String? = null,
    val doses: List<WatchDose> = emptyList(),
    val prn: List<WatchPrn> = emptyList(),
)

@Serializable
enum class WatchActionKind { TAKE, SKIP, SNOOZE, TAKE_PRN }

/** A tap on the watch, delivered whenever the phone is next reachable. */
@Serializable
data class WatchAction(
    /** Unique per tap; also the tail of the item's path, so two taps never collide. */
    val id: String,
    val kind: WatchActionKind,
    /** [WatchDose.id] for dose actions. */
    val doseId: String? = null,
    /** [WatchPrn.medicationId] for [WatchActionKind.TAKE_PRN]. */
    val medicationId: String? = null,
    /** Epoch millis of the tap: a delivery hours late must not pretend the dose was taken just now. */
    val actedAt: Long,
)

/** The one JSON configuration both apps use; malformed input decodes to null, never throws. */
object WatchJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            // Stable bytes for equal content: the Data Layer skips a put
            // whose payload did not change, which keeps the watch quiet.
            encodeDefaults = false
        }

    fun encode(snapshot: WatchSnapshot): String = json.encodeToString(WatchSnapshot.serializer(), snapshot)

    fun encode(action: WatchAction): String = json.encodeToString(WatchAction.serializer(), action)

    fun decodeSnapshot(text: String): WatchSnapshot? = decode(WatchSnapshot.serializer(), text)

    fun decodeAction(text: String): WatchAction? = decode(WatchAction.serializer(), text)

    private fun <T> decode(
        serializer: kotlinx.serialization.KSerializer<T>,
        text: String,
    ): T? =
        try {
            json.decodeFromString(serializer, text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
