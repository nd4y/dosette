package icu.nd4y.dosette.wear.tile

import androidx.wear.protolayout.ActionBuilders.AndroidActivity
import androidx.wear.protolayout.ActionBuilders.LaunchAction
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography.BODY_LARGE
import androidx.wear.protolayout.material3.Typography.BODY_SMALL
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import icu.nd4y.dosette.wear.MainActivity
import icu.nd4y.dosette.wear.R
import icu.nd4y.dosette.wear.link.GmsWearLink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.minutes

class DoseTileService : Material3TileService() {
    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val link = GmsWearLink(applicationContext)
        val snapshot = withTimeoutOrNull(4_000) { link.snapshot.first() }
        val model = tileModel(snapshot, LocalDate.now())
        val reachable = withTimeoutOrNull(1_500) { link.phoneReachable() } == true
        val lines =
            when {
                !model.hasData -> {
                    listOf(getString(R.string.tile_no_data))
                }

                model.stale -> {
                    listOf(getString(R.string.tile_stale, snapshot?.date.orEmpty()), getString(R.string.tile_open))
                }

                model.next != null -> {
                    listOf(
                        (if (model.next.carryover) getString(R.string.tile_yesterday) + " · " else "") +
                            LocalTime
                                .of(
                                    model.next.timeMinutes / 60,
                                    model.next.timeMinutes % 60,
                                ).format(DateTimeFormatter.ofPattern("HH:mm")),
                        model.next.title,
                        getString(R.string.tile_remaining, model.remaining),
                    )
                }

                else -> {
                    listOf(getString(R.string.tile_done))
                }
            }
        val click =
            Clickable
                .Builder()
                .setId("open_dosette")
                .setOnClick(
                    LaunchAction
                        .Builder()
                        .setAndroidActivity(
                            AndroidActivity
                                .Builder()
                                .setPackageName(
                                    packageName,
                                ).setClassName(MainActivity::class.java.name)
                                .build(),
                        ).build(),
                ).build()
        val content = Column.Builder().setModifiers(Modifiers.Builder().setClickable(click).build())
        lines.forEach { content.addContent(text(it.layoutString, typography = BODY_LARGE, maxLines = 3)) }
        if (!reachable && model.hasData) {
            content.addContent(
                text(getString(R.string.tile_offline).layoutString, typography = BODY_SMALL, maxLines = 2),
            )
        }
        return tile(
            freshness = 1.minutes,
            timeline =
                Timeline.fromLayoutElement(
                    primaryLayout(
                        titleSlot = { text(getString(R.string.tile_title).layoutString) },
                        mainSlot = { content.build() },
                    ),
                ),
        )
    }
}
