package icu.nd4y.dosette.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.data.settings.AppLanguage
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.ui.designsystem.strokeGlyph

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var batteryExempt by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }

    SettingsContent(
        settings = settings,
        batteryExempt = batteryExempt,
        contentPadding = contentPadding,
        onBack = onBack,
        onNagInterval = viewModel::setNagInterval,
        onSnooze = viewModel::setSnooze,
        onGrace = viewModel::setGrace,
        onTheme = viewModel::setTheme,
        onDynamicColor = viewModel::setDynamicColor,
        onLanguage = viewModel::setLanguage,
        onRequestExemption = {
            // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is legitimate
            // off-Play; falls back to the general settings list.
            val direct =
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                )
            runCatching { context.startActivity(direct) }
                .onFailure {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            batteryExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    settings: AppSettings,
    batteryExempt: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onNagInterval: (Int) -> Unit,
    onSnooze: (Int) -> Unit,
    onGrace: (Int) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onRequestExemption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(BackIcon, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        SettingsCard(title = stringResource(R.string.settings_reminders_section)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_nag_interval),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_nag_interval_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NAG_CHOICES.forEach { minutes ->
                        ChoiceChip(
                            label =
                                if (minutes == 0) {
                                    stringResource(R.string.settings_nag_off)
                                } else {
                                    stringResource(R.string.settings_min_fmt, minutes)
                                },
                            selected = settings.nagIntervalMin == minutes,
                            onClick = { onNagInterval(minutes) },
                        )
                    }
                }
            }
            CardDivider()
            ValueRow(
                title = stringResource(R.string.settings_snooze),
                value = stringResource(R.string.settings_min_fmt, settings.snoozeMin),
                options = SNOOZE_CHOICES.map { it to stringResource(R.string.settings_min_fmt, it) },
                onSelect = onSnooze,
            )
            CardDivider()
            ValueRow(
                title = stringResource(R.string.settings_grace),
                value = stringResource(R.string.settings_grace_fmt, settings.missedGraceMin),
                options = GRACE_CHOICES.map { it to stringResource(R.string.settings_grace_fmt, it) },
                onSelect = onGrace,
            )
        }

        SettingsCard(title = stringResource(R.string.settings_appearance_section)) {
            ThemeRow(current = settings.theme, onSelect = onTheme)
            CardDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_dynamic_color),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_dynamic_color_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.dynamicColor, onCheckedChange = onDynamicColor)
            }
            CardDivider()
            LanguageRow(current = settings.language, onSelect = onLanguage)
        }

        BatteryBanner(exempt = batteryExempt, onRequest = onRequestExemption)
    }
}

private val NAG_CHOICES = listOf(0, 5, 10, 15, 30)
private val SNOOZE_CHOICES = listOf(5, 10, 15, 30)
private val GRACE_CHOICES = listOf(30, 60, 120)

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun CardDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (selected) 14.dp else 20.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                androidx.compose.ui.graphics.Color.Transparent
            },
        border =
            if (selected) {
                null
            } else {
                androidx.compose.foundation
                    .BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ValueRow(
    title: String,
    value: String,
    options: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                options.forEach { (option, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menuOpen = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
        Icon(
            imageVector = ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ThemeMode.label(): String =
    when (this) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
    }

@Composable
private fun ThemeRow(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true },
    ) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box {
            Text(
                text = current.label(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ThemeMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label()) },
                        onClick = {
                            menuOpen = false
                            onSelect(mode)
                        },
                    )
                }
            }
        }
        Icon(
            imageVector = ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AppLanguage.label(): String =
    when (this) {
        AppLanguage.SYSTEM -> stringResource(R.string.language_system)
        AppLanguage.EN -> "English"
        AppLanguage.RU -> "Русский"
    }

@Composable
private fun LanguageRow(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true },
    ) {
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box {
            Text(
                text = current.label(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AppLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.label()) },
                        onClick = {
                            menuOpen = false
                            onSelect(language)
                        },
                    )
                }
            }
        }
        Icon(
            imageVector = ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun BatteryBanner(
    exempt: Boolean,
    onRequest: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color =
            if (exempt) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text =
                    stringResource(
                        if (exempt) R.string.settings_battery_ok else R.string.settings_battery_warn,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (exempt) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                modifier = Modifier.weight(1f),
            )
            if (!exempt) {
                TextButton(onClick = onRequest) {
                    Text(stringResource(R.string.settings_battery_action))
                }
            }
        }
    }
}

private val BackIcon: ImageVector by lazy {
    strokeGlyph("Back", strokeWidth = 2.2f) {
        moveTo(19f, 12f)
        lineTo(5f, 12f)
        moveTo(11f, 6f)
        lineToRelative(-6f, 6f)
        lineToRelative(6f, 6f)
    }
}

private val ChevronRight: ImageVector by lazy {
    strokeGlyph("ChevronRight") {
        moveTo(9f, 6f)
        lineToRelative(6f, 6f)
        lineToRelative(-6f, 6f)
    }
}
