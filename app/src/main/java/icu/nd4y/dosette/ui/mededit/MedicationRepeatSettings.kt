package icu.nd4y.dosette.ui.mededit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import icu.nd4y.dosette.R

@Composable
internal fun MedicationRepeatSettings(
    interval: Int?,
    onChange: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.med_repeat_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.med_repeat_hint), style = MaterialTheme.typography.bodySmall)
        FilterChip(selected = interval == null, onClick = {
            onChange(null)
        }, label = { Text(stringResource(R.string.med_repeat_global)) })
        FilterChip(selected = interval == 0, onClick = {
            onChange(0)
        }, label = { Text(stringResource(R.string.med_repeat_off)) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 5, 10, 15, 30, 60).forEach { minutes ->
                FilterChip(selected = interval == minutes, onClick = {
                    onChange(minutes)
                }, label = { Text(stringResource(R.string.med_repeat_minutes, minutes)) })
            }
        }
    }
}
