package icu.nd4y.dosette.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import icu.nd4y.dosette.R
import icu.nd4y.dosette.ui.designsystem.DosetteIcons
import icu.nd4y.dosette.ui.designsystem.strokeGlyph

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var batteryExempt by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsGranted = granted
        }

    OnboardingContent(
        notificationsGranted = notificationsGranted,
        batteryExempt = batteryExempt,
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationsGranted = true
            }
        },
        onRequestBattery = {
            // Same two-step fallback as in Settings: the direct request
            // dialog first, the general list if an OEM blocks it.
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
        onStart = viewModel::finish,
        modifier = modifier,
    )
}

@Composable
fun OnboardingContent(
    notificationsGranted: Boolean,
    batteryExempt: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestBattery: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp)),
        ) {
            Icon(
                imageVector = DosetteIcons.Pill,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            text = stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(modifier = Modifier.height(8.dp))

        FeatureRow(
            icon = BellIcon,
            title = stringResource(R.string.onboarding_feature_reminders_title),
            text = stringResource(R.string.onboarding_feature_reminders_text),
        )
        FeatureRow(
            icon = StockIcon,
            title = stringResource(R.string.onboarding_feature_stock_title),
            text = stringResource(R.string.onboarding_feature_stock_text),
        )
        FeatureRow(
            icon = FamilyIcon,
            title = stringResource(R.string.onboarding_feature_profiles_title),
            text = stringResource(R.string.onboarding_feature_profiles_text),
        )

        Spacer(modifier = Modifier.height(8.dp))

        PermissionCard(
            granted = notificationsGranted,
            title = stringResource(R.string.onboarding_notifications_title),
            text = stringResource(R.string.onboarding_notifications_text),
            actionLabel = stringResource(R.string.onboarding_allow),
            grantedLabel = stringResource(R.string.onboarding_granted),
            onRequest = onRequestNotifications,
        )
        PermissionCard(
            granted = batteryExempt,
            title = stringResource(R.string.onboarding_battery_title),
            text = stringResource(R.string.onboarding_battery_text),
            actionLabel = stringResource(R.string.onboarding_allow),
            grantedLabel = stringResource(R.string.onboarding_granted),
            onRequest = onRequestBattery,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onStart,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_start),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(15.dp)),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    granted: Boolean,
    title: String,
    text: String,
    actionLabel: String,
    grantedLabel: String,
    onRequest: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color =
            if (granted) {
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
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (granted) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (granted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        },
                )
            }
            if (granted) {
                Icon(
                    imageVector = CheckIcon,
                    contentDescription = grantedLabel,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                TextButton(onClick = onRequest) { Text(actionLabel) }
            }
        }
    }
}

private val BellIcon: ImageVector by lazy {
    strokeGlyph("Bell", strokeWidth = 2f) {
        moveTo(18f, 16f)
        lineTo(6f, 16f)
        curveTo(7f, 14.5f, 7.2f, 13f, 7.2f, 11f)
        curveTo(7.2f, 8f, 9.2f, 5.8f, 12f, 5.8f)
        curveTo(14.8f, 5.8f, 16.8f, 8f, 16.8f, 11f)
        curveTo(16.8f, 13f, 17f, 14.5f, 18f, 16f)
        moveTo(10.3f, 18.5f)
        curveTo(10.7f, 19.3f, 11.3f, 19.7f, 12f, 19.7f)
        curveTo(12.7f, 19.7f, 13.3f, 19.3f, 13.7f, 18.5f)
    }
}

private val StockIcon: ImageVector by lazy {
    strokeGlyph("Stock", strokeWidth = 2f) {
        moveTo(5f, 9f)
        lineTo(19f, 9f)
        lineTo(19f, 19f)
        lineTo(5f, 19f)
        lineTo(5f, 9f)
        moveTo(9f, 9f)
        lineTo(9f, 5f)
        lineTo(15f, 5f)
        lineTo(15f, 9f)
        moveTo(12f, 12.2f)
        lineTo(12f, 15.8f)
        moveTo(10.2f, 14f)
        lineTo(13.8f, 14f)
    }
}

private val FamilyIcon: ImageVector by lazy {
    strokeGlyph("Family", strokeWidth = 2f) {
        moveTo(9f, 11f)
        curveTo(10.7f, 11f, 12f, 9.7f, 12f, 8f)
        curveTo(12f, 6.3f, 10.7f, 5f, 9f, 5f)
        curveTo(7.3f, 5f, 6f, 6.3f, 6f, 8f)
        curveTo(6f, 9.7f, 7.3f, 11f, 9f, 11f)
        moveTo(4f, 19f)
        curveTo(4f, 16f, 6.2f, 14f, 9f, 14f)
        curveTo(11.8f, 14f, 14f, 16f, 14f, 19f)
        moveTo(15f, 11f)
        curveTo(16.7f, 11f, 18f, 9.7f, 18f, 8f)
        moveTo(16.5f, 14.2f)
        curveTo(18.6f, 14.8f, 20f, 16.6f, 20f, 19f)
    }
}

private val CheckIcon: ImageVector by lazy {
    strokeGlyph("Check", strokeWidth = 2.4f) {
        moveTo(5f, 12.5f)
        lineToRelative(4.5f, 4.5f)
        lineTo(19f, 7f)
    }
}
