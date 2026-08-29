package icu.nd4y.dosette.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import icu.nd4y.dosette.R
import icu.nd4y.dosette.ui.cabinet.CabinetScreen
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.meddetail.MedDetailScreen
import icu.nd4y.dosette.ui.mededit.MedEditScreen
import icu.nd4y.dosette.ui.navigation.CabinetKey
import icu.nd4y.dosette.ui.navigation.MedDetailKey
import icu.nd4y.dosette.ui.navigation.MedEditKey
import icu.nd4y.dosette.ui.theme.DosetteTheme

private enum class DosetteTab(
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    Today(R.string.tab_today, DosetteIcons.Today),
    Calendar(R.string.tab_calendar, DosetteIcons.Calendar),
    Cabinet(R.string.tab_cabinet, DosetteIcons.Pill),
    More(R.string.tab_more, DosetteIcons.More),
}

@Composable
fun DosetteRoot(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(DosetteTab.Cabinet.ordinal) }
    val tabs = DosetteTab.entries

    val cabinetBackStack = rememberNavBackStack(CabinetKey)
    val bottomBarVisible =
        selectedTab != DosetteTab.Cabinet.ordinal || cabinetBackStack.size <= 1

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = bottomBarVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = index == selectedTab,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (tabs[selectedTab]) {
            DosetteTab.Cabinet -> {
                NavDisplay(
                    backStack = cabinetBackStack,
                    onBack = { cabinetBackStack.removeLastOrNull() },
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    entryProvider =
                        entryProvider {
                            entry<CabinetKey> {
                                CabinetScreen(
                                    contentPadding = padding,
                                    onAddMedication = { cabinetBackStack.add(MedEditKey()) },
                                    onOpenMedication = { id -> cabinetBackStack.add(MedDetailKey(id)) },
                                )
                            }
                            entry<MedEditKey> {
                                MedEditScreen(
                                    contentPadding = padding,
                                    onDone = { cabinetBackStack.removeLastOrNull() },
                                    onBackOut = { cabinetBackStack.removeLastOrNull() },
                                )
                            }
                            entry<MedDetailKey> { key ->
                                MedDetailScreen(
                                    medicationId = key.medicationId,
                                    contentPadding = padding,
                                    onBack = { cabinetBackStack.removeLastOrNull() },
                                )
                            }
                        },
                )
            }

            DosetteTab.Today -> {
                PlaceholderScreen(
                    labelRes = DosetteTab.Today.label,
                    icon = DosetteTab.Today.icon,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }

            DosetteTab.Calendar -> {
                PlaceholderScreen(
                    labelRes = DosetteTab.Calendar.label,
                    icon = DosetteTab.Calendar.icon,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }

            DosetteTab.More -> {
                PlaceholderScreen(
                    labelRes = DosetteTab.More.label,
                    icon = DosetteTab.More.icon,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(
    @StringRes labelRes: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.placeholder_wip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun DosetteRootPreview() {
    DosetteTheme(dynamicColor = false) {
        DosetteRoot()
    }
}
