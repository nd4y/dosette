package icu.nd4y.dosette.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.model.PlaceId
import icu.nd4y.dosette.domain.nag.SnoozeTarget
import icu.nd4y.dosette.ui.common.TimeFormat
import icu.nd4y.dosette.ui.common.currentLocale
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.designsystem.EmptyState
import icu.nd4y.dosette.ui.designsystem.MedIconBox
import icu.nd4y.dosette.ui.designsystem.MedPalette
import icu.nd4y.dosette.ui.designsystem.RingCenterLabel
import icu.nd4y.dosette.ui.designsystem.SegmentedRing
import icu.nd4y.dosette.ui.designsystem.effectsSpec
import icu.nd4y.dosette.ui.designsystem.strokeGlyph
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val messageTemplate = stringResource(R.string.prn_taken_snackbar)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(viewModel) {
        viewModel.prnTaken.collect { taken ->
            val result =
                snackbarHost.showSnackbar(
                    message = messageTemplate.format(taken.medicationName),
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoPrn(taken.logId)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        TodayContent(
            state = state,
            contentPadding = contentPadding,
            onTake = viewModel::take,
            onSkip = viewModel::skip,
            onSnooze = viewModel::snooze,
            onUndo = viewModel::undo,
            onTakePrn = viewModel::takePrn,
            onSelectProfile = viewModel::selectProfile,
        )
        SnackbarHost(
            hostState = snackbarHost,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }
}

/** Stateless body — rendered directly by screenshot tests. */
@Composable
fun TodayContent(
    state: TodayUiState,
    contentPadding: PaddingValues,
    onTake: (TodayDose) -> Unit,
    onSkip: (TodayDose) -> Unit,
    onSnooze: (TodayDose, SnoozeTarget) -> Unit,
    onUndo: (TodayDose) -> Unit,
    onTakePrn: (PrnMed) -> Unit,
    onSelectProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.loading && state.doses.isEmpty() && state.prn.isEmpty()) {
        // Same horizontal inset as the list below, or switching to an
        // empty profile visibly shifts the header to the screen edge.
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 20.dp),
        ) {
            TodayHeader(state)
            ProfileChips(state, onSelectProfile)
            EmptyState(
                icon = DosetteIcons.Today,
                title = stringResource(R.string.today_empty_title),
                subtitle = stringResource(R.string.today_empty_subtitle),
            )
        }
        return
    }

    val slots = state.doses.groupBy { it.slot }

    LazyColumn(
        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "header") { TodayHeader(state) }
        if (state.profiles.size > 1) {
            item(key = "profiles") { ProfileChips(state, onSelectProfile) }
        }
        item(key = "hero") { HeroCard(state) }

        DaySlot.entries.forEach { slot ->
            val doses = slots[slot] ?: return@forEach
            item(key = "slot-$slot") {
                SlotHeader(slot = slot, doses = doses, modifier = Modifier.padding(top = 10.dp))
            }
            doses.forEach { dose ->
                item(key = "dose-${dose.key.encode()}") {
                    val fade = effectsSpec<Float>()
                    AnimatedContent(
                        targetState = dose,
                        contentKey = { it.status },
                        transitionSpec = {
                            (fadeIn(fade) togetherWith fadeOut(fade)) using SizeTransform(clip = false)
                        },
                        label = "dose",
                        modifier = Modifier.animateItem(),
                    ) { animatedDose ->
                        if (animatedDose.status == DoseUiStatus.PENDING) {
                            PendingDoseCard(
                                dose = animatedDose,
                                snoozePlaces = state.snoozePlaces,
                                onTake = { onTake(animatedDose) },
                                onSkip = { onSkip(animatedDose) },
                                onSnooze = { target -> onSnooze(animatedDose, target) },
                            )
                        } else {
                            ActedDoseRow(
                                dose = animatedDose,
                                onTake = { onTake(animatedDose) },
                                onUndo = { onUndo(animatedDose) },
                            )
                        }
                    }
                }
            }
        }

        if (state.prn.isNotEmpty()) {
            item(key = "prn-header") {
                Text(
                    text = stringResource(R.string.today_prn_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            state.prn.forEach { prnMed ->
                item(key = "prn-${prnMed.medicationId}") {
                    PrnRow(prnMed = prnMed, onTake = { onTakePrn(prnMed) })
                }
            }
        }
    }
}

@Composable
private fun TodayHeader(state: TodayUiState) {
    val locale = currentLocale()
    val formatted =
        remember(state.date, locale) {
            val raw = state.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
            if (locale.language == "ru") raw.replaceFirstChar { it.lowercase(locale) } else raw
        }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = stringResource(R.string.tab_today),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeroCard(state: TodayUiState) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            SegmentedRing(
                total = state.plannedCount,
                done = state.takenCount,
                doneColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface,
            ) {
                RingCenterLabel(done = state.takenCount, total = state.plannedCount)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.today_taken_of, state.takenCount, state.plannedCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                val pending = state.doses.count { it.status == DoseUiStatus.PENDING }
                Text(
                    text =
                        when {
                            state.nextDoseTime != null -> {
                                stringResource(R.string.today_next_dose, state.nextDoseTime.format(TimeFormat))
                            }

                            pending > 0 -> {
                                pluralStringResource(R.plurals.today_pending_left, pending, pending)
                            }

                            else -> {
                                stringResource(R.string.today_all_done)
                            }
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun SlotHeader(
    slot: DaySlot,
    doses: List<TodayDose>,
    modifier: Modifier = Modifier,
) {
    val label =
        when (slot) {
            DaySlot.MORNING -> stringResource(R.string.slot_morning)
            DaySlot.AFTERNOON -> stringResource(R.string.slot_afternoon)
            DaySlot.EVENING -> stringResource(R.string.slot_evening)
            DaySlot.NIGHT -> stringResource(R.string.slot_night)
        }
    val firstTime = doses.minOf { it.time }.format(TimeFormat)
    val allActed = doses.none { it.status == DoseUiStatus.PENDING }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "$label · $firstTime",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
        if (allActed) {
            Text(
                text = stringResource(R.string.slot_all_done),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ActedDoseRow(
    dose: TodayDose,
    onTake: () -> Unit,
    onUndo: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { menuOpen = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            MedIconBox(form = dose.form, colorSeed = dose.colorSeed, size = 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = dose.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = actedSubtitle(dose),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusCircle(dose.status)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (dose.status != DoseUiStatus.TAKEN) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mark_taken)) },
                    onClick = {
                        menuOpen = false
                        onTake()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.undo_mark)) },
                onClick = {
                    menuOpen = false
                    onUndo()
                },
            )
        }
    }
}

@Composable
private fun actedSubtitle(dose: TodayDose): String {
    val doseText =
        listOfNotNull(dose.strengthText, stringResource(R.string.unit_pieces, dose.amountText))
            .joinToString(" · ")
    val statusText =
        when (dose.status) {
            DoseUiStatus.TAKEN -> {
                dose.actedTime?.let { stringResource(R.string.dose_taken_at, it.format(TimeFormat)) }
            }

            DoseUiStatus.SKIPPED -> {
                stringResource(R.string.dose_skipped_label)
            }

            DoseUiStatus.MISSED -> {
                stringResource(R.string.dose_missed_label)
            }

            DoseUiStatus.PENDING -> {
                null
            }
        }
    return listOfNotNull(doseText.ifBlank { null }, statusText).joinToString(" · ")
}

@Composable
private fun StatusCircle(status: DoseUiStatus) {
    val (bg, fg, icon) =
        when (status) {
            DoseUiStatus.TAKEN -> {
                Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, CheckIcon)
            }

            DoseUiStatus.SKIPPED -> {
                Triple(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    SkipIcon,
                )
            }

            DoseUiStatus.MISSED -> {
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    CrossIcon,
                )
            }

            DoseUiStatus.PENDING -> {
                Triple(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurface,
                    CheckIcon,
                )
            }
        }
    Box(
        modifier = Modifier.size(28.dp).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ProfileChips(
    state: TodayUiState,
    onSelectProfile: (String) -> Unit,
) {
    if (state.profiles.size < 2) return
    val dark = isSystemInDarkTheme()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
    ) {
        state.profiles.forEach { profile ->
            val selected = profile.id == state.activeProfileId
            val palette = MedPalette.resolve(profile.colorSeed, dark)
            Surface(
                onClick = { onSelectProfile(profile.id) },
                shape = RoundedCornerShape(if (selected) 14.dp else 20.dp),
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(22.dp)
                                .background(palette.container, CircleShape),
                    ) {
                        Text(
                            text = profile.name.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onContainer,
                        )
                    }
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingDoseCard(
    dose: TodayDose,
    snoozePlaces: Set<PlaceId>,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: (SnoozeTarget) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            MedIconBox(form = dose.form, colorSeed = dose.colorSeed, size = 44.dp)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = dose.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        listOfNotNull(
                            dose.strengthText,
                            stringResource(R.string.unit_pieces, dose.amountText),
                            dose.instructions,
                        ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TakeSplitButton(
                snoozePlaces = snoozePlaces,
                onTake = onTake,
                onSkip = onSkip,
                onSnooze = onSnooze,
            )
        }
    }
}

@Composable
private fun TakeSplitButton(
    snoozePlaces: Set<PlaceId>,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: (SnoozeTarget) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            onClick = onTake,
            shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = stringResource(R.string.action_take),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        Box {
            Surface(
                onClick = { menuOpen = true },
                shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(modifier = Modifier.width(34.dp).height(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = ChevronDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_skip)) },
                    onClick = {
                        menuOpen = false
                        onSkip()
                    },
                )
                SNOOZE_MINUTE_CHOICES.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.snooze_for_min, minutes)) },
                        onClick = {
                            menuOpen = false
                            onSnooze(SnoozeTarget.ForMinutes(minutes))
                        },
                    )
                }
                if (PlaceId.HOME in snoozePlaces) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.snooze_until_home)) },
                        onClick = {
                            menuOpen = false
                            onSnooze(SnoozeTarget.UntilPlace(PlaceId.HOME))
                        },
                    )
                }
                if (PlaceId.WORK in snoozePlaces) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.snooze_until_work)) },
                        onClick = {
                            menuOpen = false
                            onSnooze(SnoozeTarget.UntilPlace(PlaceId.WORK))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrnRow(
    prnMed: PrnMed,
    onTake: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border =
            androidx.compose.foundation
                .BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            MedIconBox(form = prnMed.form, colorSeed = prnMed.colorSeed, size = 32.dp)
            Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = listOfNotNull(prnMed.name, prnMed.strengthText).joinToString(" "),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.schedule_as_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onTake,
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = PlusSmall,
                        contentDescription = stringResource(R.string.prn_take),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private val CheckIcon: ImageVector by lazy {
    strokeGlyph("Check", strokeWidth = 3f) {
        moveTo(5f, 13f)
        lineToRelative(4f, 4f)
        lineTo(19f, 7f)
    }
}

private val CrossIcon: ImageVector by lazy {
    strokeGlyph("Cross", strokeWidth = 3f) {
        moveTo(6f, 6f)
        lineToRelative(12f, 12f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }
}

private val SkipIcon: ImageVector by lazy {
    strokeGlyph("Skip", strokeWidth = 2.6f) {
        moveTo(6f, 12f)
        lineToRelative(12f, 0f)
    }
}

private val SNOOZE_MINUTE_CHOICES = listOf(10, 30, 60)

private val ChevronDown: ImageVector by lazy {
    strokeGlyph("ChevronDown", strokeWidth = 2.6f) {
        moveTo(6f, 9f)
        lineToRelative(6f, 6f)
        lineToRelative(6f, -6f)
    }
}

private val PlusSmall: ImageVector by lazy {
    strokeGlyph("Plus", strokeWidth = 2.4f) {
        moveTo(12f, 5f)
        lineToRelative(0f, 14f)
        moveTo(5f, 12f)
        lineToRelative(14f, 0f)
    }
}
