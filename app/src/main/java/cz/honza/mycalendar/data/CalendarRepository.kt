package cz.honza.mycalendar.data

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import cz.honza.mycalendar.CalendarApplication
import cz.honza.mycalendar.widget.CalendarWidget
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class CalendarRepository(private val context: Application) {
    private data class Record(val value: JSONObject?, val mutation: String? = null) {
        fun encoded(): String = JSONObject()
            .put("value", value ?: JSONObject.NULL)
            .put("mutation", mutation ?: JSONObject.NULL)
            .toString()
    }

    private val preferences = context.getSharedPreferences("calendar_records", Context.MODE_PRIVATE)
    private val settings = context.getSharedPreferences("sync_credentials", Context.MODE_PRIVATE)
    private val syncLock = Mutex()
    private val records = preferences.all.mapNotNull { (id, raw) ->
        if (!id.contains('/') || raw !is String) return@mapNotNull null
        runCatching {
            val record = JSONObject(raw)
            id to Record(record.optJSONObject("value"), record.optString("mutation").takeIf { !record.isNull("mutation") })
        }.getOrNull()
    }.toMap(linkedMapOf())
    private var cursor = preferences.getLong("cursor", 0)
    private val _connection = MutableStateFlow<SyncConnection?>(
        settings.getString("endpoint", null)?.let { endpoint ->
            settings.getString("token", null)?.let { SyncConnection.parse(endpoint, it) }
        }
    )
    private val _status = MutableStateFlow(SyncStatus.IDLE)

    init {
        if (!preferences.getBoolean("imported_day_notes", false)) {
            val oldNotes = context.getSharedPreferences("day_notes", Context.MODE_PRIVATE)
            preferences.edit {
                for ((date, value) in oldNotes.all) {
                    if (value !is String || value.isEmpty() || runCatching { LocalDate.parse(date) }.isFailure) continue
                    val id = "note/$date"
                    if (id !in records) {
                        val record = newRecord(JSONObject().put("text", value))
                        records[id] = record
                        putString(id, record.encoded())
                    }
                }
                putBoolean("imported_day_notes", true)
            }
        }
        CalendarSyncJob.configure(context, _connection.value != null)
    }

    private val _data = MutableStateFlow(readData())
    val data = _data.asStateFlow()
    val connection = _connection.asStateFlow()
    val status = _status.asStateFlow()

    fun saveNote(date: LocalDate, text: String) {
        save("note/$date", text.takeIf { it.isNotEmpty() }?.let { JSONObject().put("text", it) })
    }

    fun saveEvent(event: CalendarEvent) = save("event/${event.id}", event.toJson())
    fun deleteEvent(id: String) = save("event/$id", null)

    fun saveGroup(group: CalendarGroup) {
        save("calendar/${group.id}", JSONObject().put("name", group.name ?: JSONObject.NULL).put("color", group.color.name))
    }

    fun deleteGroup(id: String) {
        if (defaultGroups.none { it.id == id }) save("calendar/$id", null)
    }

    @Synchronized
    private fun save(id: String, value: JSONObject?) {
        if (records[id]?.value?.toString() == value?.toString()) return
        val record = newRecord(value)
        records[id] = record
        preferences.edit { putString(id, record.encoded()) }
        publish()
        CalendarSyncJob.enqueueChangeIfConnected(context, _connection.value != null)
    }

    @Synchronized
    fun setConnection(connection: SyncConnection?) {
        if (_connection.value == connection) return
        // Keep the local calendar when disconnecting. A different server receives the existing calendar.
        val lastWorkspace = preferences.getString("workspace", null)
        if (connection != null && lastWorkspace != connection.workspace) {
            cursor = 0
            preferences.edit {
                putLong("cursor", cursor)
                putString("workspace", connection.workspace)
                for ((id, record) in records) {
                    if (record.value == null) {
                        // Deletions from the previous server have no meaning in the new workspace.
                        remove(id)
                    } else {
                        val pending = newRecord(record.value)
                        records[id] = pending
                        putString(id, pending.encoded())
                    }
                }
            }
            records.entries.removeAll { it.value.value == null }
        }
        settings.edit {
            if (connection == null) clear()
            else putString("endpoint", connection.endpoint).putString("token", connection.token)
        }
        _connection.value = connection
        _status.value = SyncStatus.IDLE
        CalendarSyncJob.configure(context, connection != null)
    }

    suspend fun sync(): Boolean = syncLock.withLock {
        val current = _connection.value ?: return@withLock true
        _status.value = SyncStatus.SYNCING
        try {
            val finished = withContext(Dispatchers.IO) {
                var more = true
                var pages = 0
                while (more && pages < 16) {
                    ensureActive()
                    val snapshot = snapshot(current) ?: return@withContext true
                    val response = exchange(current, snapshot)
                    more = apply(current, snapshot, response)
                    pages++
                }
                !more
            }
            if (_connection.value == current) _status.value = if (finished) SyncStatus.SYNCED else SyncStatus.IDLE
            finished
        } catch (cancelled: CancellationException) {
            if (_connection.value == current) _status.value = SyncStatus.IDLE
            throw cancelled
        } catch (failure: SyncFailure) {
            if (_connection.value == current) _status.value = failure.status
            failure.status != SyncStatus.UNAVAILABLE
        }
    }

    @Synchronized
    private fun snapshot(connection: SyncConnection): SyncSnapshot? {
        if (_connection.value != connection) return null
        val pending = records.asSequence().filter { it.value.mutation != null }.take(64).map { (id, record) ->
            SyncChange(id, record.value, record.mutation)
        }.toList()
        return SyncSnapshot(cursor, pending)
    }

    @Synchronized
    private fun apply(connection: SyncConnection, snapshot: SyncSnapshot, response: SyncResponse): Boolean {
        if (_connection.value != connection) return false
        var changed = false
        preferences.edit {
            for (sent in snapshot.changes) {
                val existing = records[sent.id] ?: continue
                if (existing.mutation == sent.mutation) {
                    val acknowledged = Record(existing.value)
                    records[sent.id] = acknowledged
                    putString(sent.id, acknowledged.encoded())
                }
            }
            for (remote in response.changes) {
                val existing = records[remote.id]
                if (existing?.mutation != null) continue
                if (existing?.value?.toString() != remote.value?.toString()) {
                    val record = Record(remote.value)
                    records[remote.id] = record
                    putString(remote.id, record.encoded())
                    changed = true
                }
            }
            cursor = response.cursor
            putLong("cursor", cursor)
        }
        if (changed) publish()
        return response.more || records.values.any { it.mutation != null }
    }

    private fun publish() {
        val previous = _data.value
        val updated = readData()
        _data.value = updated
        val today = LocalDate.now()
        if (previous.notes[today] != updated.notes[today] || previous.events != updated.events) {
            CalendarWidget.refresh(context, updated)
        }
    }

    private fun readData(): CalendarData {
        val notes = mutableMapOf<LocalDate, String>()
        val groups = defaultGroups.associateByTo(linkedMapOf()) { it.id }
        val events = mutableListOf<CalendarEvent>()
        for ((key, record) in records) {
            val value = record.value ?: continue
            val id = key.substringAfter('/')
            runCatching {
                when (key.substringBefore('/')) {
                    "note" -> notes[LocalDate.parse(id)] = value.getString("text")
                    "calendar" -> {
                        val name = if (value.isNull("name")) null else value.getString("name")
                        val color = CalendarColor.valueOf(value.getString("color"))
                        groups[id] = CalendarGroup(id, name, color, groups[id]?.defaultName)
                    }
                    "event" -> events += eventFromJson(id, value)
                }
            }
        }
        return CalendarData(notes, groups.values.toList(), events.sortedWith(compareBy(CalendarEvent::date, CalendarEvent::start, CalendarEvent::id)))
    }

    private fun newRecord(value: JSONObject?) = Record(value, UUID.randomUUID().toString())

    companion object {
        fun get(context: Context): CalendarRepository = (context.applicationContext as CalendarApplication).calendarRepository
    }
}
