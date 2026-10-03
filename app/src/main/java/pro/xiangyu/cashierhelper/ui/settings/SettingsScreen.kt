package pro.xiangyu.cashierhelper.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import pro.xiangyu.cashierhelper.R
import pro.xiangyu.cashierhelper.capture.ServiceState
import pro.xiangyu.cashierhelper.config.ConfigState
import pro.xiangyu.cashierhelper.ui.MainUiState
import pro.xiangyu.cashierhelper.ui.SaveState
import pro.xiangyu.cashierhelper.ui.theme.statusColors

@Composable
fun SettingsScreen(
    state: MainUiState,
    versionName: String,
    onBack: () -> Unit,
    onTestAndSave: (baseUrl: String, apiKey: String, force: Boolean) -> Unit,
    onEdited: () -> Unit,
    onFixService: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixBattery: () -> Unit,
    onOpenCashier: (baseUrl: String) -> Unit,
) {
    val saved = when (val config = state.config) {
        is ConfigState.Ready -> config.config.baseUrl to config.config.apiKey
        is ConfigState.KeyUnreadable -> config.baseUrl to ""
        is ConfigState.Missing -> config.baseUrl to ""
        null -> "" to ""
    }
    var baseUrl by rememberSaveable(saved.first) { mutableStateOf(saved.first) }
    var apiKey by rememberSaveable(saved.second) { mutableStateOf(saved.second) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val testing = state.save is SaveState.Testing

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_connection), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_connection_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.config is ConfigState.KeyUnreadable) {
                Text(
                    stringResource(R.string.settings_key_unreadable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; onEdited() },
                label = { Text(stringResource(R.string.settings_server_address)) },
                placeholder = { Text(stringResource(R.string.settings_server_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; onEdited() },
                label = { Text(stringResource(R.string.settings_api_key)) },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showKey = !showKey }) {
                    Text(stringResource(if (showKey) R.string.action_hide else R.string.action_show))
                }
                TextButton(onClick = {
                    clipboard.getText()?.text?.trim()?.let { apiKey = it; onEdited() }
                }) { Text(stringResource(R.string.action_paste)) }
                TextButton(
                    onClick = { onOpenCashier(baseUrl) },
                    enabled = baseUrl.isNotBlank(),
                ) { Text(stringResource(R.string.action_open_cashier)) }
            }
            SaveResult(state.save)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onTestAndSave(baseUrl, apiKey, false) }, enabled = !testing) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.action_test_and_save))
                    }
                }
                val failed = state.save as? SaveState.Failed
                if (failed?.canForce == true) {
                    OutlinedButton(onClick = { onTestAndSave(baseUrl, apiKey, true) }) {
                        Text(stringResource(R.string.action_save_anyway))
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_status), style = MaterialTheme.typography.titleMedium)
            StatusRow(
                label = stringResource(R.string.settings_screenshot_service),
                value = when (state.service) {
                    ServiceState.RUNNING -> stringResource(R.string.service_running)
                    ServiceState.ENABLED_NOT_RUNNING -> stringResource(R.string.service_not_running)
                    ServiceState.OFF -> stringResource(R.string.service_off)
                },
                good = state.service == ServiceState.RUNNING,
                actionLabel = stringResource(R.string.action_fix),
                onAction = onFixService,
            )
            StatusRow(
                label = stringResource(R.string.settings_notifications),
                value = stringResource(if (state.notifications.isVisible) R.string.toggle_on else R.string.toggle_off),
                good = state.notifications.isVisible,
                actionLabel = stringResource(R.string.action_turn_on),
                onAction = onFixNotifications,
            )
            StatusRow(
                label = stringResource(R.string.settings_battery),
                value = stringResource(if (state.batteryUnrestricted) R.string.battery_unrestricted else R.string.battery_restricted),
                good = state.batteryUnrestricted,
                actionLabel = stringResource(R.string.action_set),
                onAction = onFixBattery,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            stringResource(R.string.settings_version, versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SaveResult(save: SaveState) {
    when (save) {
        is SaveState.Failed -> Text(save.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        SaveState.Saved -> Text(
            stringResource(R.string.settings_saved),
            color = MaterialTheme.statusColors.success,
            style = MaterialTheme.typography.bodyMedium,
        )
        SaveState.Testing -> Text(
            stringResource(R.string.settings_testing),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        SaveState.Idle -> Unit
    }
}

@Composable
private fun StatusRow(label: String, value: String, good: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (good) MaterialTheme.statusColors.success else MaterialTheme.statusColors.warning,
            )
        }
        if (!good) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
