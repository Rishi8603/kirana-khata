package com.rishi.kiranaledger.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rishi.kiranaledger.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun today(): Long = LocalDate.now().toEpochDay()

private fun epochDayToUtcMillis(day: Long): Long = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToEpochDay(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

/**
 * Calendar dialog returning an epoch day.
 * [pastOnly]: entries can be back-dated but not future-dated. [futureOnly]: due dates are today or later.
 * [extraAction] adds a third button, e.g. "Remove due date".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(
    initialDay: Long,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
    pastOnly: Boolean = false,
    futureOnly: Boolean = false,
    extraAction: Pair<String, () -> Unit>? = null,
) {
    val todayMillis = epochDayToUtcMillis(today())
    val state = rememberDatePickerState(
        initialSelectedDateMillis = epochDayToUtcMillis(initialDay),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = when {
                pastOnly -> utcTimeMillis <= todayMillis
                futureOnly -> utcTimeMillis >= todayMillis
                else -> true
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPicked(utcMillisToEpochDay(it)) } ?: onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            if (extraAction != null) {
                TextButton(onClick = extraAction.second) { Text(extraAction.first) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    ) {
        DatePicker(state = state)
    }
}
