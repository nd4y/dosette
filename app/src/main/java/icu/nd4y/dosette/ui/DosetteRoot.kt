package icu.nd4y.dosette.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import icu.nd4y.dosette.R
import icu.nd4y.dosette.ui.appointments.AppointmentEditScreen
import icu.nd4y.dosette.ui.appointments.AppointmentsScreen
import icu.nd4y.dosette.ui.backup.BackupScreen
import icu.nd4y.dosette.ui.cabinet.CabinetScreen
import icu.nd4y.dosette.ui.calendar.CalendarScreen
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.designsystem.rememberDirectionalMotion
import icu.nd4y.dosette.ui.meddetail.MedDetailScreen
import icu.nd4y.dosette.ui.mededit.MedEditScreen
import icu.nd4y.dosette.ui.more.MoreScreen
import icu.nd4y.dosette.ui.navigation.AppointmentEditKey
import icu.nd4y.dosette.ui.navigation.AppointmentsKey
import icu.nd4y.dosette.ui.navigation.BackupKey
import icu.nd4y.dosette.ui.navigation.CabinetKey
import icu.nd4y.dosette.ui.navigation.MedDetailKey
import icu.nd4y.dosette.ui.navigation.MedEditKey
import icu.nd4y.dosette.ui.navigation.MoreKey
import icu.nd4y.dosette.ui.navigation.ProfilesKey
import icu.nd4y.dosette.ui.navigation.SettingsKey
import icu.nd4y.dosette.ui.navigation.StatsKey
import icu.nd4y.dosette.ui.profiles.ProfilesScreen
import icu.nd4y.dosette.ui.settings.SettingsScreen
import icu.nd4y.dosette.ui.stats.StatsScreen
import icu.nd4y.dosette.ui.theme.DosetteTheme
import icu.nd4y.dosette.ui.today.TodayScreen

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
    val moreBackStack = rememberNavBackStack(MoreKey)
    val bottomBarVisible =
        when (tabs[selectedTab]) {
            DosetteTab.Cabinet -> cabinetBackStack.size <= 1
            DosetteTab.More -> moreBackStack.size <= 1
            else -> true
        }

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
        val tabMotion = rememberDirectionalMotion()
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { tabMotion.transform(forward = targetState > initialState) },
            label = "tab",
        ) { tabIndex ->
            TabContent(
                tab = tabs[tabIndex],
                padding = padding,
                cabinetBackStack = cabinetBackStack,
                moreBackStack = moreBackStack,
            )
        }
    }
}

@Composable
private fun TabContent(
    tab: DosetteTab,
    padding: PaddingValues,
    cabinetBackStack: NavBackStack<NavKey>,
    moreBackStack: NavBackStack<NavKey>,
) {
    val navMotion = rememberDirectionalMotion()
    when (tab) {
        DosetteTab.Cabinet -> {
            NavDisplay(
                backStack = cabinetBackStack,
                onBack = { cabinetBackStack.removeLastOrNull() },
                transitionSpec = { navMotion.transform(forward = true) },
                popTransitionSpec = { navMotion.transform(forward = false) },
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
            TodayScreen(contentPadding = padding)
        }

        DosetteTab.Calendar -> {
            CalendarScreen(contentPadding = padding)
        }

        DosetteTab.More -> {
            NavDisplay(
                backStack = moreBackStack,
                onBack = { moreBackStack.removeLastOrNull() },
                transitionSpec = { navMotion.transform(forward = true) },
                popTransitionSpec = { navMotion.transform(forward = false) },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider =
                    entryProvider {
                        entry<MoreKey> {
                            MoreScreen(
                                contentPadding = padding,
                                onOpenProfiles = { moreBackStack.add(ProfilesKey) },
                                onOpenSettings = { moreBackStack.add(SettingsKey) },
                                onOpenAppointments = { moreBackStack.add(AppointmentsKey) },
                                onOpenStats = { moreBackStack.add(StatsKey) },
                                onOpenBackup = { moreBackStack.add(BackupKey) },
                            )
                        }
                        entry<BackupKey> {
                            BackupScreen(
                                contentPadding = padding,
                                onBack = { moreBackStack.removeLastOrNull() },
                            )
                        }
                        entry<AppointmentsKey> {
                            AppointmentsScreen(
                                contentPadding = padding,
                                onBack = { moreBackStack.removeLastOrNull() },
                                onAdd = { moreBackStack.add(AppointmentEditKey()) },
                                onOpen = { id -> moreBackStack.add(AppointmentEditKey(id)) },
                            )
                        }
                        entry<AppointmentEditKey> { key ->
                            AppointmentEditScreen(
                                appointmentId = key.appointmentId,
                                contentPadding = padding,
                                onDone = { moreBackStack.removeLastOrNull() },
                            )
                        }
                        entry<StatsKey> {
                            StatsScreen(
                                contentPadding = padding,
                                onBack = { moreBackStack.removeLastOrNull() },
                            )
                        }
                        entry<SettingsKey> {
                            SettingsScreen(
                                contentPadding = padding,
                                onBack = { moreBackStack.removeLastOrNull() },
                            )
                        }
                        entry<ProfilesKey> {
                            ProfilesScreen(
                                contentPadding = padding,
                                onBack = { moreBackStack.removeLastOrNull() },
                            )
                        }
                    },
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
