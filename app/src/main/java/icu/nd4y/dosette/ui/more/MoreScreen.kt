package icu.nd4y.dosette.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.BuildConfig
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.model.Profile
import icu.nd4y.dosette.ui.designsystem.MedPalette
import icu.nd4y.dosette.ui.designsystem.strokeGlyph

@Composable
fun MoreScreen(
    contentPadding: PaddingValues,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppointments: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    MoreContent(
        profiles = profiles,
        contentPadding = contentPadding,
        onOpenProfiles = onOpenProfiles,
        onOpenSettings = onOpenSettings,
        onOpenAppointments = onOpenAppointments,
        onOpenStats = onOpenStats,
        modifier = modifier,
    )
}

@Composable
fun MoreContent(
    profiles: List<Profile>,
    contentPadding: PaddingValues,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppointments: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.tab_more),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        ProfilesCard(profiles = profiles, onClick = onOpenProfiles)

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                MoreRow(
                    icon = AppointmentsIcon,
                    title = stringResource(R.string.more_appointments),
                    subtitle = null,
                    enabled = true,
                    onClick = onOpenAppointments,
                )
                Divider()
                MoreRow(
                    icon = StatsIcon,
                    title = stringResource(R.string.more_stats),
                    subtitle = null,
                    enabled = true,
                    onClick = onOpenStats,
                )
                Divider()
                MoreRow(
                    icon = BackupIcon,
                    title = stringResource(R.string.more_backup),
                    subtitle = stringResource(R.string.more_backup_hint),
                    enabled = false,
                    onClick = {},
                )
                Divider()
                MoreRow(
                    icon = SettingsIcon,
                    title = stringResource(R.string.more_settings),
                    subtitle = stringResource(R.string.more_settings_hint),
                    enabled = true,
                    onClick = onOpenSettings,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MoreRow(
                icon = InfoIcon,
                title = stringResource(R.string.more_about),
                subtitle = "Dosette ${BuildConfig.VERSION_NAME}",
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun ProfilesCard(
    profiles: List<Profile>,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row {
                val dark = isSystemInDarkTheme()
                profiles.take(3).forEachIndexed { index, profile ->
                    val color = MedPalette.resolve(profile.colorSeed, dark)
                    Box(
                        modifier =
                            Modifier
                                .offset(x = (-12 * index).dp)
                                .size(44.dp)
                                .background(color.container, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = profile.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = color.onContainer,
                        )
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.more_profiles),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = profiles.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(
                imageVector = ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint =
                        if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!enabled) {
                Text(
                    text = stringResource(R.string.coming_soon_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                Icon(
                    imageVector = ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

private val AppointmentsIcon: ImageVector by lazy {
    strokeGlyph("Appointments", strokeWidth = 2f) {
        moveTo(9f, 3f)
        lineToRelative(6f, 0f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
        lineToRelative(0f, 1f)
        lineToRelative(2f, 0f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
        lineToRelative(0f, 11f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
        lineTo(5f, 21f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
        lineTo(3f, 8f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
        lineToRelative(2f, 0f)
        lineToRelative(0f, -1f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
        close()
        moveTo(12f, 10f)
        lineToRelative(0f, 6f)
        moveTo(9f, 13f)
        lineToRelative(6f, 0f)
    }
}

private val StatsIcon: ImageVector by lazy {
    strokeGlyph("Stats", strokeWidth = 2.2f) {
        moveTo(4f, 20f)
        lineToRelative(0f, -10f)
        moveTo(10f, 20f)
        lineToRelative(0f, -16f)
        moveTo(16f, 20f)
        lineToRelative(0f, -9f)
        moveTo(22f, 20f)
        lineTo(2f, 20f)
    }
}

private val BackupIcon: ImageVector by lazy {
    strokeGlyph("Backup", strokeWidth = 2f) {
        moveTo(12f, 3f)
        lineToRelative(0f, 12f)
        moveTo(8f, 11f)
        lineToRelative(4f, 4f)
        lineToRelative(4f, -4f)
        moveTo(5f, 21f)
        lineToRelative(14f, 0f)
    }
}

private val SettingsIcon: ImageVector by lazy {
    strokeGlyph("Settings", strokeWidth = 2f) {
        moveTo(12f, 9f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, 6f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, -6f)
        close()
        moveTo(19f, 12f)
        arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.1f, -1.2f)
        lineToRelative(2f, -1.5f)
        lineToRelative(-2f, -3.6f)
        lineToRelative(-2.4f, 1f)
        arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, -1.2f)
        lineTo(14f, 3f)
        lineToRelative(-4f, 0f)
        lineToRelative(-0.5f, 2.5f)
        arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, 1.2f)
        lineToRelative(-2.4f, -1f)
        lineToRelative(-2f, 3.6f)
        lineToRelative(2f, 1.5f)
        arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 2.4f)
        lineToRelative(-2f, 1.5f)
        lineToRelative(2f, 3.6f)
        lineToRelative(2.4f, -1f)
        arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 1.2f)
        lineTo(10f, 21f)
        lineToRelative(4f, 0f)
        lineToRelative(0.5f, -2.5f)
        arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, -1.2f)
        lineToRelative(2.4f, 1f)
        lineToRelative(2f, -3.6f)
        lineToRelative(-2f, -1.5f)
        arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19f, 12f)
        close()
    }
}

private val InfoIcon: ImageVector by lazy {
    strokeGlyph("Info", strokeWidth = 2f) {
        moveTo(12f, 3f)
        arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, 18f)
        arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, -18f)
        close()
        moveTo(12f, 11f)
        lineToRelative(0f, 6f)
        moveTo(12f, 7.5f)
        lineToRelative(0f, 0.5f)
    }
}

private val ChevronRight: ImageVector by lazy {
    strokeGlyph("ChevronRight") {
        moveTo(9f, 6f)
        lineToRelative(6f, 6f)
        lineToRelative(-6f, 6f)
    }
}
