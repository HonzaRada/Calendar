package cz.honza.mycalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import cz.honza.mycalendar.R
import cz.honza.mycalendar.data.CalendarData
import cz.honza.mycalendar.data.CalendarEvent
import cz.honza.mycalendar.data.clockText
import cz.honza.mycalendar.ui.theme.forCalendar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HourlySchedule(
    dates: List<LocalDate>,
    selected: LocalDate,
    data: CalendarData,
    onAdd: (LocalDate, Int) -> Unit,
    onDay: (LocalDate) -> Unit,
    onEdit: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
    scrollWithin: Boolean = false
) {
    val week = dates.size > 1
    val byDate = remember(data.events, dates) { dates.associateWith(data::eventsOn) }
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceIn(1f, 2f)
    val hourHeight = with(density) { (56.dp * fontScale).roundToPx().toDp() }
    val headerHeight = 56.dp * fontScale
    val timeWidth = 56.dp * fontScale

    OutlinedCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Text(
            text = stringResource(R.string.hourly_schedule),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).semantics { heading() },
            style = MaterialTheme.typography.titleMedium
        )
        HorizontalDivider()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewport = max(1.dp, maxWidth - timeWidth)
            val minimumDayWidth = if (maxWidth >= 600.dp) 76.dp * fontScale else 104.dp
            val dayWidth = if (week) max(minimumDayWidth, viewport / dates.size) else viewport
            val columnWidthPx = with(LocalDensity.current) { dayWidth.roundToPx() }
            val horizontal = rememberScrollState()
            val vertical = rememberScrollState()
            LaunchedEffect(week, dayWidth, selected) {
                if (week) {
                    // Show the selected day first when the seven columns do not fit.
                    horizontal.scrollTo((dates.indexOf(selected).coerceAtLeast(0) * columnWidthPx)
                        .coerceAtMost(horizontal.maxValue))
                } else {
                    horizontal.scrollTo(0)
                }
            }
            Column {
                if (week) {
                    Row {
                        Spacer(Modifier.width(timeWidth))
                        Row(Modifier.weight(1f).horizontalScroll(horizontal)) {
                            for (day in dates) {
                                Box(Modifier.width(dayWidth)) {
                                    ScheduleDayHeading(day, day == selected, headerHeight) { onDay(day) }
                                }
                            }
                        }
                    }
                }
                Row(if (scrollWithin) Modifier.verticalScroll(vertical) else Modifier) {
                    Column(Modifier.width(timeWidth)) {
                        for (hour in 0..23) {
                            Box(Modifier.height(hourHeight).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                Text(
                                    clockText(hour * 60),
                                    modifier = Modifier.padding(top = 3.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Row(Modifier.weight(1f).horizontalScroll(horizontal)) {
                        for (day in dates) {
                            Box(Modifier.width(dayWidth)) {
                                ScheduleDay(day, dayWidth, hourHeight, byDate[day].orEmpty(), data, onAdd, onEdit)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleDayHeading(day: LocalDate, selected: Boolean, height: Dp, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dayText = day.format(DateTimeFormatter.ofPattern("EEE", locale))
    val number = day.format(DateTimeFormatter.ofPattern("d", locale))
    val fullDate = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = 1.dp, vertical = 2.dp)
            .semantics { contentDescription = fullDate },
        shape = MaterialTheme.shapes.small,
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(dayText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(number, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ScheduleDay(
    day: LocalDate,
    width: Dp,
    hourHeight: Dp,
    events: List<CalendarEvent>,
    data: CalendarData,
    onAdd: (LocalDate, Int) -> Unit,
    onEdit: (CalendarEvent) -> Unit
) {
    val placed = remember(events) { placeEvents(events) }
    val locale = LocalConfiguration.current.locales[0]
    val shortDate = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(hourHeight * 24)) {
        Column {
            for (hour in 0..23) {
                val description = stringResource(R.string.add_event_at, shortDate, clockText(hour * 60))
                Box(
                    Modifier.fillMaxWidth().height(hourHeight)
                        .clickable(role = Role.Button) { onAdd(day, hour * 60) }
                        .semantics { contentDescription = description }
                ) {
                    HorizontalDivider()
                    Spacer(Modifier.width(1.dp).height(hourHeight).background(colors.outlineVariant))
                }
            }
        }
        for (item in placed) {
            val event = item.event
            val group = data.group(event.groupId)
            val palette = colors.forCalendar(group)
            val laneWidth = width / item.lanes
            val height = hourHeight * ((item.visibleEnd - item.visibleStart) / 60f)
            val timing = stringResource(R.string.date_range, clockText(event.start), clockText(event.end))
            val description = stringResource(R.string.schedule_event, event.displayTitle, timing, groupName(group))
            Surface(
                onClick = { onEdit(event) },
                modifier = Modifier
                    .offset(x = laneWidth * item.lane, y = hourHeight * (item.visibleStart / 60f))
                    .width(laneWidth).height(height).padding(horizontal = 2.dp, vertical = 1.dp)
                    .semantics { contentDescription = description },
                color = palette.container,
                contentColor = palette.onContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row {
                    Spacer(Modifier.width(3.dp).height(height).background(palette.accent))
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text(event.displayTitle, style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.visibleEnd - event.start >= 42) {
                            Text(timing, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private data class PlacedEvent(
    val event: CalendarEvent,
    val visibleStart: Int,
    val visibleEnd: Int,
    val lane: Int,
    val lanes: Int
)

private fun placeEvents(events: List<CalendarEvent>): List<PlacedEvent> {
    val result = mutableListOf<PlacedEvent>()
    val laneEnds = mutableListOf<Int>()
    val group = mutableListOf<PlacedEvent>()

    fun finishGroup() {
        result += group.map { it.copy(lanes = laneEnds.size) }
        laneEnds.clear()
        group.clear()
    }

    for (event in events.sortedWith(compareBy(CalendarEvent::start, CalendarEvent::end))) {
        val visibleStart = event.start.coerceAtMost(1410)
        val visibleEnd = maxOf(event.end, visibleStart + 30)
        if (laneEnds.all { it <= visibleStart }) finishGroup()
        val lane = laneEnds.indexOfFirst { it <= visibleStart }
        if (lane == -1) {
            group += PlacedEvent(event, visibleStart, visibleEnd, laneEnds.size, 0)
            laneEnds += visibleEnd
        } else {
            group += PlacedEvent(event, visibleStart, visibleEnd, lane, 0)
            laneEnds[lane] = visibleEnd
        }
    }
    finishGroup()
    return result
}
