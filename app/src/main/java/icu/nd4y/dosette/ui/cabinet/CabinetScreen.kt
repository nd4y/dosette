package icu.nd4y.dosette.ui.cabinet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.ui.common.asText
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.designsystem.EmptyState
import icu.nd4y.dosette.ui.designsystem.MedIconBox
import icu.nd4y.dosette.ui.designsystem.StockBadge
import icu.nd4y.dosette.ui.designsystem.strokeGlyph

@Composable
fun CabinetScreen(
    contentPadding: PaddingValues,
    onAddMedication: () -> Unit,
    onOpenMedication: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CabinetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        if (!state.loading && state.active.isEmpty() && state.archived.isEmpty()) {
            EmptyState(
                icon = DosetteIcons.Pill,
                title = stringResource(R.string.cabinet_empty_title),
                subtitle = stringResource(R.string.cabinet_empty_subtitle),
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            var archivedExpanded by rememberSaveable { mutableStateOf(false) }
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = contentPadding.calculateTopPadding() + 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 96.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.active, key = { it.id }) { card ->
                    MedCardRow(card = card, onClick = { onOpenMedication(card.id) })
                }
                if (state.archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        ArchivedHeader(
                            count = state.archived.size,
                            expanded = archivedExpanded,
                            onToggle = { archivedExpanded = !archivedExpanded },
                        )
                    }
                    items(state.archived, key = { it.id }) { card ->
                        AnimatedVisibility(visible = archivedExpanded) {
                            MedCardRow(card = card, onClick = { onOpenMedication(card.id) })
                        }
                    }
                }
            }
        }

        AddFab(
            onClick = onAddMedication,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 20.dp,
                        bottom = contentPadding.calculateBottomPadding() + 20.dp,
                    ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LargeFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    ) {
        Icon(
            imageVector = PlusIcon,
            contentDescription = stringResource(R.string.cabinet_add),
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun MedCardRow(
    card: MedCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            MedIconBox(form = card.form, colorSeed = card.colorSeed)
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = listOfNotNull(card.name, card.strengthText).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val scheduleText = card.schedule.asText()
                if (scheduleText.isNotEmpty()) {
                    Text(
                        text = scheduleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StockLine(card)
            }
            Icon(
                imageVector = ChevronIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StockLine(card: MedCard) {
    when {
        card.stockUnits != null && card.lowStock -> {
            StockBadge(
                text =
                    stringResource(
                        R.string.stock_left,
                        card.stockUnits,
                    ) + " · " + stringResource(R.string.stock_low),
                warning = true,
            )
        }

        card.stockUnits != null -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StockBadge(text = stringResource(R.string.stock_left, card.stockUnits), warning = false)
                card.daysOfSupply?.let { days ->
                    Text(
                        text = pluralStringResource(R.plurals.stock_days_left, days, days),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        else -> {
            Text(
                text = stringResource(R.string.stock_untracked),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ArchivedHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.cabinet_archived),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        StockBadge(text = count.toString(), warning = false)
        Icon(
            imageVector = if (expanded) ChevronUpIcon else ChevronDownIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

private val PlusIcon: ImageVector by lazy {
    strokeGlyph("Plus") {
        moveTo(12f, 5f)
        lineToRelative(0f, 14f)
        moveTo(5f, 12f)
        lineToRelative(14f, 0f)
    }
}

private val ChevronIcon: ImageVector by lazy {
    strokeGlyph("ChevronRight") {
        moveTo(9f, 6f)
        lineToRelative(6f, 6f)
        lineToRelative(-6f, 6f)
    }
}

private val ChevronDownIcon: ImageVector by lazy {
    strokeGlyph("ChevronDown") {
        moveTo(6f, 9f)
        lineToRelative(6f, 6f)
        lineToRelative(6f, -6f)
    }
}

private val ChevronUpIcon: ImageVector by lazy {
    strokeGlyph("ChevronUp") {
        moveTo(6f, 15f)
        lineToRelative(6f, -6f)
        lineToRelative(6f, 6f)
    }
}
