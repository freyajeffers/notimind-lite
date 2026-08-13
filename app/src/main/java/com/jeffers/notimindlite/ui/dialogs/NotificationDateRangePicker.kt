package com.jeffers.notimindlite.ui.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDateRangePicker(
    onDismiss: () -> Unit,
    onDateRangeSelected: (startMs: Long?, endMs: Long?) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis?.let { it + 86399999L }
                    onDateRangeSelected(start, end)
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDateRangeSelected(null, null) }) {
                Text("Clear")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = { Text("Select Date Range") },
            showModeToggle = false
        )
    }
}
