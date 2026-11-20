package cz.honza.mycalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.honza.mycalendar.R
import cz.honza.mycalendar.data.CalendarColor
import cz.honza.mycalendar.data.CalendarEvent
import cz.honza.mycalendar.data.CalendarGroup
import cz.honza.mycalendar.data.clockText
import cz.honza.mycalendar.data.eventStickers
import cz.honza.mycalendar.data.parseClock
import cz.honza.mycalendar.ui.theme.forCalendar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventEditor(
    id: String,
    event: CalendarEvent?,
    defaultDate: LocalDate,
    defaultStart: Int,
    groups: List<CalendarGroup>,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit,
    onDelete: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(event?.title.orEmpty()) }
    var day by rememberSaveable { mutableLongStateOf((event?.date ?: defaultDate).toEpochDay()) }
    var start by rememberSaveable { mutableStateOf(clockText(event?.start ?: defaultStart)) }
    var end by rememberSaveable { mutableStateOf(clockText(event?.end ?: (defaultStart + 60).coerceAtMost(1440))) }
    var groupId by rememberSaveable { mutableStateOf(event?.groupId ?: "other") }
    var sticker by rememberSaveable { mutableStateOf(event?.sticker.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    val group = groups.firstOrNull { it.id == groupId } ?: groups.first { it.id == "other" }
    val date = LocalDate.ofEpochDay(day)
    val locale = LocalConfiguration.current.locales[0]
    val formattedDate = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val startMinute = parseClock(start)?.takeIf { it < 1440 }
    val endMinute = parseClock(end)
    val validTimes = startMinute != null && endMinute != null && endMinute > startMinute

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = day * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { day = Math.floorDiv(it, 86_400_000L) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.select_date)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(pickerState) }
        return
    }

    if (showDelete) {
        ConfirmDelete(R.string.delete_event, R.string.delete_event_message, { showDelete = false }, onDelete)
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (event == null) R.string.new_event else R.string.edit_event)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title, { title = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.event_name)) },
                    singleLine = true, isError = submitted && title.isBlank()
                )
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(stringResource(R.string.event_date, formattedDate))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField(start, { start = it }, R.string.event_start, submitted && startMinute == null, Modifier.weight(1f))
                    TimeField(end, { end = it }, R.string.event_end, submitted && !validTimes, Modifier.weight(1f))
                }
                if (submitted && !validTimes) {
                    Text(stringResource(R.string.invalid_times), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.event_calendar), style = MaterialTheme.typography.labelLarge)
                GroupSelector(groups, group) { groupId = it.id }

                Text(stringResource(R.string.event_sticker), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = sticker.isEmpty(), onClick = { sticker = "" },
                        label = { Text(stringResource(R.string.no_sticker)) }
                    )
                    for (emoji in eventStickers) {
                        FilterChip(
                            selected = sticker == emoji,
                            onClick = { sticker = emoji },
                            label = { Text(emoji) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (title.isNotBlank() && startMinute != null && endMinute != null && validTimes) {
                    onSave(CalendarEvent(id, date, startMinute, endMinute, title.trim(), group.id, sticker))
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (event != null) {
                    TextButton(onClick = { showDelete = true }) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

@Composable
private fun TimeField(value: String, onChange: (String) -> Unit, label: Int, invalid: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value, onChange, modifier,
        label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(R.string.time_format)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        singleLine = true, isError = invalid
    )
}

@Composable
private fun GroupSelector(groups: List<CalendarGroup>, selected: CalendarGroup, onSelected: (CalendarGroup) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { GroupLabel(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (group in groups) {
                DropdownMenuItem(
                    text = { GroupLabel(group) },
                    onClick = { onSelected(group); expanded = false }
                )
            }
        }
    }
}

@Composable
internal fun GroupEditor(
    group: CalendarGroup?,
    onDismiss: () -> Unit,
    onSave: (CalendarGroup) -> Unit,
    onDelete: (String) -> Unit
) {
    val originalName = group?.let { groupName(it) }.orEmpty()
    var name by rememberSaveable { mutableStateOf(originalName) }
    var color by rememberSaveable { mutableStateOf(group?.color ?: CalendarColor.PRIMARY) }
    var delete by rememberSaveable { mutableStateOf(false) }

    if (delete && group != null) {
        ConfirmDelete(R.string.delete_calendar, R.string.delete_calendar_message, { delete = false }) { onDelete(group.id) }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (group == null) R.string.new_calendar else R.string.edit_calendar)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.calendar_name)) }, singleLine = true)
                Text(stringResource(R.string.calendar_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (tone in CalendarColor.entries) {
                        FilterChip(selected = color == tone, onClick = { color = tone },
                            label = { Text(stringResource(tone.label)) }, leadingIcon = { ColorSwatch(tone) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val savedName = if (group?.name == null && group?.defaultName != null && name.trim() == originalName) null else name.trim()
                onSave(CalendarGroup(group?.id ?: UUID.randomUUID().toString(), savedName, color, group?.defaultName))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (group != null && group.defaultName == null) {
                    TextButton(onClick = { delete = true }) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

@Composable
private fun ConfirmDelete(title: Int, message: Int, onCancel: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
internal fun groupName(group: CalendarGroup): String =
    group.name ?: stringResource(group.defaultName ?: R.string.calendar_other)

@Composable
internal fun GroupLabel(group: CalendarGroup) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ColorSwatch(group.color)
        Text(groupName(group))
    }
}

@Composable
private fun ColorSwatch(color: CalendarColor) {
    Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.forCalendar(color).accent, MaterialTheme.shapes.small))
}
