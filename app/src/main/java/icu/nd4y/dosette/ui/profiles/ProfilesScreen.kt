package icu.nd4y.dosette.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.model.Profile
import icu.nd4y.dosette.ui.designsystem.MedPalette
import icu.nd4y.dosette.ui.designsystem.strokeGlyph

@Composable
fun ProfilesScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Profile?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                text = stringResource(R.string.profiles_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        state.profiles.forEach { profile ->
            ProfileRow(
                profile = profile,
                active = profile.id == state.activeProfileId,
                onClick = { viewModel.setActive(profile.id) },
                onEdit = { editing = profile },
                onDelete =
                    if (state.profiles.size > 1) {
                        fun() {
                            deleting = profile
                        }
                    } else {
                        null
                    },
            )
        }

        TextButton(onClick = { creating = true }) {
            Text(stringResource(R.string.profiles_add))
        }
    }

    if (creating || editing != null) {
        ProfileDialog(
            profile = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { id, name, colorSeed ->
                viewModel.save(id, name, colorSeed)
                creating = false
                editing = null
            },
        )
    }

    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.profile_delete)) },
            text = { Text(stringResource(R.string.profile_delete_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(profile.id)
                    deleting = null
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    active: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val color = MedPalette.resolve(profile.colorSeed, isSystemInDarkTheme())
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color =
            if (active) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        border =
            if (active) {
                null
            } else {
                androidx.compose.foundation
                    .BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(color.container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profile.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = color.onContainer,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (active) {
                    Text(
                        text = stringResource(R.string.profiles_active),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = EditIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = DeleteIcon,
                        contentDescription = stringResource(R.string.profile_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    profile: Profile?,
    onDismiss: () -> Unit,
    onSave: (String?, String, Int) -> Unit,
) {
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var colorSeed by remember { mutableStateOf(profile?.colorSeed ?: 0) }
    val dark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (profile == null) R.string.profiles_add else R.string.more_profiles),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(MedPalette.size) { seed ->
                        val color = MedPalette.resolve(seed, dark)
                        Box(
                            modifier =
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color.container)
                                    .clickable { colorSeed = seed },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (colorSeed == seed) {
                                Icon(
                                    imageVector = CheckIcon,
                                    contentDescription = null,
                                    tint = color.onContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(profile?.id, name, colorSeed) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
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

private val EditIcon: ImageVector by lazy {
    strokeGlyph("Edit", strokeWidth = 2f) {
        moveTo(4f, 20f)
        lineToRelative(4f, 0f)
        lineTo(19f, 9f)
        arcToRelative(2.1f, 2.1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -3f, -3f)
        lineTo(5f, 17f)
        close()
        moveTo(14f, 7f)
        lineToRelative(3f, 3f)
    }
}

private val DeleteIcon: ImageVector by lazy {
    strokeGlyph("Delete", strokeWidth = 2f) {
        moveTo(4f, 7f)
        lineToRelative(16f, 0f)
        moveTo(9f, 7f)
        lineToRelative(0f, -2f)
        lineToRelative(6f, 0f)
        lineToRelative(0f, 2f)
        moveTo(6f, 7f)
        lineToRelative(1f, 14f)
        lineToRelative(10f, 0f)
        lineToRelative(1f, -14f)
        moveTo(10f, 11f)
        lineToRelative(0f, 6f)
        moveTo(14f, 11f)
        lineToRelative(0f, 6f)
    }
}

private val CheckIcon: ImageVector by lazy {
    strokeGlyph("Check", strokeWidth = 3f) {
        moveTo(5f, 13f)
        lineToRelative(4f, 4f)
        lineTo(19f, 7f)
    }
}
