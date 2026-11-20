package cz.honza.mycalendar.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cz.honza.mycalendar.R
import cz.honza.mycalendar.data.CalendarGroup
import cz.honza.mycalendar.data.CalendarRepository
import cz.honza.mycalendar.data.SyncConnection
import cz.honza.mycalendar.data.SyncStatus
import kotlinx.coroutines.launch

@Composable
internal fun CalendarSettings(
    groups: List<CalendarGroup>,
    onDismiss: () -> Unit,
    onEditGroup: (String?) -> Unit,
    onSync: () -> Unit,
    onWidget: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.calendars), style = MaterialTheme.typography.titleMedium)
                for (group in groups) {
                    TextButton(onClick = { onEditGroup(group.id) }) { GroupLabel(group) }
                }
                OutlinedButton(onClick = { onEditGroup(null) }) { Text(stringResource(R.string.new_calendar)) }
                HorizontalDivider()
                TextButton(onClick = onSync) { Text(stringResource(R.string.sync_title)) }
                TextButton(onClick = onWidget) { Text(stringResource(R.string.pin_widget)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = SyncConnection.parse(url, token)
                if (updated == null) invalid = true
                else {
                    repository.setConnection(updated)
                    scope.launch { repository.sync() }
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}
