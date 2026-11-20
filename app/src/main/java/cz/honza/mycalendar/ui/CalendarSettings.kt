package cz.honza.mycalendar.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cz.honza.mycalendar.R
import cz.honza.mycalendar.data.AppThemeMode
import cz.honza.mycalendar.data.CalendarGroup
import cz.honza.mycalendar.data.CalendarRepository
import cz.honza.mycalendar.data.SyncConnection
import cz.honza.mycalendar.data.SyncStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_day_arrow), stringResource(R.string.back), Modifier.rotate(180f))
                }
            },
            actions = actions
        )
    }) { insets ->
        Box(
            modifier = Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun CalendarSettings(
    repository: CalendarRepository,
    groups: List<CalendarGroup>,
    onDismiss: () -> Unit,
    onEditGroup: (String?) -> Unit,
    onSync: () -> Unit,
    onWidget: () -> Unit
) {
    val theme by repository.themeMode.collectAsState()
    val context = LocalContext.current
    SettingsPage(stringResource(R.string.settings), onDismiss) {
        Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            for (mode in AppThemeMode.entries) {
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(theme == mode, role = Role.RadioButton) { repository.setThemeMode(mode) }
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(selected = theme == mode, onClick = null)
                    Text(stringResource(mode.label))
                }
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.calendars), style = MaterialTheme.typography.titleMedium)
        for (group in groups) {
            TextButton(onClick = { onEditGroup(group.id) }) { GroupLabel(group) }
        }
        OutlinedButton(onClick = { onEditGroup(null) }) { Text(stringResource(R.string.new_calendar)) }
        HorizontalDivider()
        Text(stringResource(R.string.event_reminders), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text(stringResource(R.string.reminder_android_settings)) }
        if (Build.VERSION.SDK_INT >= 31) {
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.fromParts("package", context.packageName, null)))
            }) { Text(stringResource(R.string.reminder_exact_settings)) }
        }
        HorizontalDivider()
        TextButton(onClick = onSync) { Text(stringResource(R.string.sync_title)) }
        TextButton(onClick = onWidget) { Text(stringResource(R.string.pin_widget)) }
    }
}

@Composable
internal fun SyncSettings(repository: CalendarRepository, onDismiss: () -> Unit) {
    val connection by repository.connection.collectAsState()
    val status by repository.status.collectAsState()
    var url by rememberSaveable { mutableStateOf(connection?.endpoint.orEmpty()) }
    var token by remember { mutableStateOf(connection?.token.orEmpty()) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var localNetworkAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 37 ||
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED)
    }
    var showLocalNetworkOption by remember(connection) { mutableStateOf(status == SyncStatus.UNAVAILABLE) }
    LaunchedEffect(status) {
        if (status == SyncStatus.UNAVAILABLE) showLocalNetworkOption = true
        if (status == SyncStatus.SYNCED) showLocalNetworkOption = false
    }
    val requestLocalNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        localNetworkAllowed = allowed
        if (allowed) scope.launch { repository.sync() }
    }

    val statusText = if (connection == null) R.string.sync_disconnected else when (status) {
        SyncStatus.IDLE -> R.string.sync_ready
        SyncStatus.SYNCING -> R.string.sync_in_progress
        SyncStatus.SYNCED -> R.string.sync_success
        SyncStatus.UNAVAILABLE -> R.string.sync_unavailable
        SyncStatus.UNAUTHORIZED -> R.string.sync_unauthorized
        SyncStatus.INVALID_RESPONSE -> R.string.sync_invalid_response
    }

    SettingsPage(stringResource(R.string.sync_title), onDismiss, actions = {
        TextButton(onClick = {
            val updated = SyncConnection.parse(url, token)
            if (updated == null) invalid = true
            else {
                repository.setConnection(updated)
                scope.launch { repository.sync() }
            }
        }) { Text(stringResource(R.string.save)) }
    }) {
        Text(stringResource(R.string.sync_instructions))
        OutlinedTextField(
            url, { url = it; invalid = false }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sync_address)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true, isError = invalid
        )
        OutlinedTextField(
            token, { token = it; invalid = false }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sync_key)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, isError = invalid
        )
        if (invalid) {
            Text(stringResource(R.string.sync_invalid_settings), color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(statusText), style = MaterialTheme.typography.bodyMedium)
        if (connection != null) {
            OutlinedButton(
                onClick = { scope.launch { repository.sync() } },
                enabled = status != SyncStatus.SYNCING
            ) { Text(stringResource(R.string.sync_now)) }
            if (Build.VERSION.SDK_INT >= 37 && !localNetworkAllowed && showLocalNetworkOption) {
                TextButton(onClick = { requestLocalNetwork.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }) {
                    Text(stringResource(R.string.sync_allow_local_network))
                }
            }
            TextButton(onClick = {
                repository.setConnection(null)
                token = ""
            }) { Text(stringResource(R.string.sync_disconnect)) }
        }
    }
}
