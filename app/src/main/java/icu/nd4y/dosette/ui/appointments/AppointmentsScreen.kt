package icu.nd4y.dosette.ui.appointments

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.nd4y.dosette.R
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.ui.common.currentLocale
import icu.nd4y.dosette.ui.designsystem.ScreenHeader
import java.time.format.DateTimeFormatter

@Composable
fun AppointmentsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppointmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AppointmentsContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onAdd = onAdd,
        onOpen = onOpen,
        modifier = modifier,
    )
}

@Composable
fun AppointmentsContent(
    state: AppointmentsUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = stringResource(R.string.appointments_title), onBack = onBack)

        if (!state.loading && state.upcoming.isEmpty()) {
            Text(
                text = stringResource(R.string.appointments_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        state.upcoming.forEach { appointment ->
            AppointmentRow(appointment = appointment, onClick = { onOpen(appointment.id) })
        }

        TextButton(onClick = onAdd) {
            Text(stringResource(R.string.appointments_add))
        }
    }
}

@Composable
private fun AppointmentRow(
    appointment: Appointment,
    onClick: () -> Unit,
) {
    val locale = currentLocale()
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("d", locale) }
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLL", locale) }
    val timeFormat = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .size(52.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(14.dp),
                        ).padding(top = 6.dp),
            ) {
                Text(
                    text = dayFormat.format(appointment.date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = monthFormat.format(appointment.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = appointment.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle =
                    listOfNotNull(appointment.doctorName, appointment.location)
                        .joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = timeFormat.format(appointment.time),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
