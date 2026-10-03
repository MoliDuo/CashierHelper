package pro.xiangyu.cashierhelper.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import pro.xiangyu.cashierhelper.config.ConfigState
import pro.xiangyu.cashierhelper.ui.SaveState
import pro.xiangyu.cashierhelper.ui.theme.statusColors

/** The address and key fields with their test-and-save button; used by settings and by the setup guide. */
@Composable
fun ConnectionForm(
    config: ConfigState?,
    save: SaveState,
    onTestAndSave: (baseUrl: String, apiKey: String, force: Boolean) -> Unit,
    onEdited: () -> Unit,
    onOpenCashier: (baseUrl: String) -> Unit,
) {
    val saved = when (config) {
        is ConfigState.Ready -> config.config.baseUrl to config.config.apiKey
        is ConfigState.KeyUnreadable -> config.baseUrl to ""
        is ConfigState.Missing -> config.baseUrl to ""
        null -> "" to ""
    }
    var baseUrl by rememberSaveable(saved.first) { mutableStateOf(saved.first) }
    var apiKey by rememberSaveable(saved.second) { mutableStateOf(saved.second) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val testing = save is SaveState.Testing

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (config is ConfigState.KeyUnreadable) {
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
            TextButton(onClick = { clipboard.getText()?.text?.trim()?.let { apiKey = it; onEdited() } }) {
                Text(stringResource(R.string.action_paste))
            }
            TextButton(onClick = { onOpenCashier(baseUrl) }, enabled = baseUrl.isNotBlank()) {
                Text(stringResource(R.string.action_open_cashier))
            }
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onTestAndSave(baseUrl, apiKey, false) }, enabled = !testing) {
                if (testing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.action_test_and_save))
                }
            }
            if ((save as? SaveState.Failed)?.canForce == true) {
                OutlinedButton(onClick = { onTestAndSave(baseUrl, apiKey, true) }) {
                    Text(stringResource(R.string.action_save_anyway))
                }
            }
        }
    }
}
