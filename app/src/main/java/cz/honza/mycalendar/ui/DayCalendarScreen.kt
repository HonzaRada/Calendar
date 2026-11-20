package cz.honza.mycalendar.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.honza.mycalendar.R
import cz.honza.mycalendar.data.CalendarData
import cz.honza.mycalendar.data.CalendarEvent
import cz.honza.mycalendar.data.CalendarRepository
import cz.honza.mycalendar.data.weekStart
import cz.honza.mycalendar.widget.CalendarWidget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.UUID

@Composable
internal fun CalendarScreen(data: CalendarData, repository: CalendarRepository, widgetRequest: Int) {
    var selectedDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var weekly by rememberSaveable { mutableStateOf(false) }
    var editingEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var newEventDate by rememberSaveable { mutableLongStateOf(selectedDay) }
    var newEventStart by rememberSaveable { mutableIntStateOf(9 * 60) }
    var settingsPage by rememberSaveable { mutableStateOf("") }
    val date = LocalDate.ofEpochDay(selectedDay)
    val focus = LocalFocusManager.current
    val context = LocalContext.current

    fun selectDate(day: LocalDate) {
        focus.clearFocus()
        selectedDay = day.toEpochDay()
    }

    fun addEvent(day: LocalDate, minute: Int) {
        newEventDate = day.toEpochDay()
        newEventStart = minute
        editingEventId = UUID.randomUUID().toString()
    }

    LaunchedEffect(widgetRequest) {
        if (widgetRequest > 0) {
            weekly = false
            editingEventId = null
            settingsPage = ""
            selectDate(LocalDate.now())
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { insets ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val wide = maxWidth >= 680.dp
            val short = maxHeight < 400.dp
            val pageScroll = rememberScrollState()
            val timelineScroll = rememberScrollState()
            val noteScroll = rememberScrollState()
            val pageModifier = if (short) Modifier.verticalScroll(pageScroll) else Modifier
            val timelineModifier = if (short) Modifier else Modifier.verticalScroll(timelineScroll)
            val horizontalPadding = if (wide) 24.dp else 12.dp

            Column(
                modifier = Modifier.widthIn(max = 1360.dp).fillMaxWidth().then(pageModifier)
                    .padding(horizontal = horizontalPadding).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalendarControls(
                    weekly,
                    onModeChange = { focus.clearFocus(); weekly = it },
                    onAdd = { addEvent(date, 9 * 60) },
                    onSettings = { settingsPage = "settings" }
                )
                CalendarHeader(date, weekly, wide, ::selectDate)

                val body = if (short) Modifier else Modifier.weight(1f)
                val dates = if (weekly) List(7) { date.weekStart().plusDays(it.toLong()) } else listOf(date)
                val showDay: (LocalDate) -> Unit = { day ->
                    selectDate(day)
                    weekly = false
                }
                val editEvent: (CalendarEvent) -> Unit = { event ->
                    newEventDate = event.date.toEpochDay()
                    newEventStart = event.start
                    editingEventId = event.id
                }
                val saveNote: (String) -> Unit = { repository.saveNote(date, it) }

                if (weekly) {
                    HourlySchedule(dates, date, data, ::addEvent, showDay, editEvent,
                        modifier = body.padding(bottom = 16.dp), scrollWithin = !short)
                } else if (wide) {
                    Row(body.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HourlySchedule(
                            dates, date, data, ::addEvent, showDay, editEvent,
                            Modifier.weight(1.65f).then(timelineModifier).padding(bottom = 16.dp)
                        )
                        val scroll = if (short) Modifier else Modifier.verticalScroll(noteScroll)
                        DayNote(date, data.notes[date].orEmpty(), saveNote, 5,
                            Modifier.weight(1f).then(scroll).padding(bottom = 16.dp))
                    }
                } else {
                    Column(
                        modifier = body.fillMaxWidth().then(timelineModifier).padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DayNote(date, data.notes[date].orEmpty(), saveNote, 2)
                        HourlySchedule(dates, date, data, ::addEvent, showDay, editEvent)
                    }
                }
            }
        }
    }

    editingEventId?.let { id ->
        key(id) {
            EventEditor(
                id, data.events.firstOrNull { it.id == id },
                LocalDate.ofEpochDay(newEventDate), newEventStart, data.groups,
                onDismiss = { editingEventId = null },
                onSave = { repository.saveEvent(it); editingEventId = null },
                onDelete = { repository.deleteEvent(id); editingEventId = null }
            )
        }
    }

    when (settingsPage) {
        "" -> Unit
        "settings" -> CalendarSettings(
            data.groups,
            onDismiss = { settingsPage = "" },
            onEditGroup = { settingsPage = "group/${it.orEmpty()}" },
            onSync = { settingsPage = "sync" },
            onWidget = {
                if (!CalendarWidget.requestPin(context)) {
                    Toast.makeText(context, R.string.widget_launcher_hint, Toast.LENGTH_LONG).show()
                }
            }
        )
        "sync" -> SyncSettings(repository) { settingsPage = "settings" }
        else -> {
            val groupId = settingsPage.substringAfter('/').ifEmpty { null }
            val group = data.groups.firstOrNull { it.id == groupId }
            GroupEditor(
                group,
                onDismiss = { settingsPage = "settings" },
                onSave = { repository.saveGroup(it); settingsPage = "settings" },
                onDelete = { repository.deleteGroup(it); settingsPage = "settings" }
            )
        }
    }
}

@Composable
private fun CalendarControls(
    weekly: Boolean,
    onModeChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = !weekly, onClick = { onModeChange(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text(stringResource(R.string.view_day)) }
            SegmentedButton(
                selected = weekly, onClick = { onModeChange(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text(stringResource(R.string.view_week)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onAdd) { Text(stringResource(R.string.add_event)) }
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_calendar_settings), stringResource(R.string.settings))
            }
        }
    }
}

@Composable
private fun CalendarHeader(date: LocalDate, weekly: Boolean, wide: Boolean, onDateChange: (LocalDate) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        val padding = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        if (wide) {
            Row(
                modifier = padding,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DateHeading(date, weekly, Modifier.weight(1f))
                CalendarNavigation(date, weekly, onDateChange)
            }
        } else {
            Column(modifier = padding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DateHeading(date, weekly)
                CalendarNavigation(date, weekly, onDateChange)
            }
        }
    }
}

@Composable
private fun DateHeading(date: LocalDate, weekly: Boolean, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    val shortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    val title = if (weekly) stringResource(R.string.week_heading) else weekday
    val dateText = if (weekly) {
        val monday = date.weekStart()
        stringResource(R.string.date_range, monday.format(shortDate), monday.plusDays(6).format(shortDate))
    } else date.format(longDate)

    Column(modifier.semantics(mergeDescendants = true) {
        heading()
        liveRegion = LiveRegionMode.Polite
    }) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(dateText, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CalendarNavigation(date: LocalDate, weekly: Boolean, onDateChange: (LocalDate) -> Unit) {
    val arrow = painterResource(R.drawable.ic_day_arrow)
    val step = if (weekly) 7L else 1L
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(onClick = { onDateChange(date.minusDays(step)) }) {
            Icon(
                arrow,
                stringResource(if (weekly) R.string.previous_week else R.string.previous_day),
                modifier = Modifier.rotate(180f)
            )
        }
        Button(onClick = { onDateChange(LocalDate.now()) }) { Text(stringResource(R.string.today)) }
        OutlinedIconButton(onClick = { onDateChange(date.plusDays(step)) }) {
            Icon(arrow, stringResource(if (weekly) R.string.next_week else R.string.next_day))
        }
    }
}

@Composable
private fun DayNote(date: LocalDate, note: String, save: (String) -> Unit, minLines: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        key(date) {
            OutlinedTextField(
                value = note, onValueChange = save, modifier = Modifier.fillMaxWidth().padding(12.dp),
                label = { Text(stringResource(R.string.day_note)) },
                supportingText = { Text(stringResource(R.string.day_note_autosave)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = MaterialTheme.shapes.medium,
                minLines = minLines, maxLines = 8
            )
        }
    }
}
