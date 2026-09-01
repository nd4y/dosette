package icu.nd4y.dosette.ui.designsystem

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset

/**
 * Remembers where inside an anchor the last press landed, so a
 * [androidx.compose.material3.DropdownMenu] can open under the finger
 * instead of at the anchor's start corner — on a full-width row the
 * default puts the menu at the far edge of the screen.
 */
class PressPosition {
    /** Pass as the DropdownMenu offset; zero until the first press. */
    var menuOffset: DpOffset by mutableStateOf(DpOffset.Zero)
        internal set
}

/**
 * Watches presses in the initial pass (without consuming them, so the
 * anchor's own clickable keeps working) and records the position where
 * a menu anchored to this element should open.
 */
fun Modifier.trackPressFor(position: PressPosition): Modifier =
    pointerInput(position) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
            // A DropdownMenu drops below the anchor by default; pulling it
            // up by the anchor height puts it at the press point itself.
            position.menuOffset =
                DpOffset(
                    down.position.x.toDp(),
                    down.position.y.toDp() - size.height.toDp(),
                )
        }
    }
