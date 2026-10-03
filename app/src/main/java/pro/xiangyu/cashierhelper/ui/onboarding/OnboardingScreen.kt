package pro.xiangyu.cashierhelper.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pro.xiangyu.cashierhelper.R
import pro.xiangyu.cashierhelper.ui.MainUiState
import pro.xiangyu.cashierhelper.ui.settings.ConnectionForm
import pro.xiangyu.cashierhelper.ui.theme.statusColors

class OnboardingActions(
    val onStart: () -> Unit,
    val onNext: () -> Unit,
    val onSkipAll: () -> Unit,
    val onFinish: () -> Unit,
    val onTestAndSave: (baseUrl: String, apiKey: String, force: Boolean) -> Unit,
    val onEdited: () -> Unit,
    val onOpenCashier: (baseUrl: String) -> Unit,
    val onOpenAccessibility: () -> Unit,
    val onOpenAppInfo: () -> Unit,
    val onAllowNotifications: () -> Unit,
    val onOpenBattery: () -> Unit,
)

@Composable
fun OnboardingScreen(state: MainUiState, explainRestricted: Boolean, actions: OnboardingActions) {
    val step = state.onboardingStep
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (step == Step.WELCOME) {
            Welcome(actions)
            return@Column
        }
        val position = OnboardingFlow.position(step) ?: 1
        val total = OnboardingFlow.numbered.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.onboarding_step_of, position, total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.onSkipAll) { Text(stringResource(R.string.onboarding_skip_all)) }
        }
        LinearProgressIndicator(
            progress = { position / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        when (step) {
            Step.WELCOME -> Unit
            Step.CONNECT -> ConnectStep(state, actions)
            Step.SERVICE -> ServiceStep(state, explainRestricted, actions)
            Step.NOTIFICATIONS -> NotificationsStep(state, actions)
            Step.BATTERY -> BatteryStep(state, actions)
            Step.SIDE_KEY -> SideKeyStep(state, actions)
        }
    }
}

@Composable
private fun Welcome(actions: OnboardingActions) {
    Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineLarge)
    Text(
        stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = actions.onStart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_start))
    }
    TextButton(onClick = actions.onSkipAll, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_skip_all))
    }
}

@Composable
private fun StepHeader(title: Int, body: Int?) {
    Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
    if (body != null) {
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Done(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.statusColors.success,
    )
}

@Composable
private fun Tip(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConnectStep(state: MainUiState, actions: OnboardingActions) {
    StepHeader(R.string.onboarding_connect_title, R.string.settings_connection_help)
    ConnectionForm(
        config = state.config,
        save = state.save,
        onTestAndSave = actions.onTestAndSave,
        onEdited = actions.onEdited,
        onOpenCashier = actions.onOpenCashier,
    )
}

@Composable
private fun ServiceStep(state: MainUiState, explainRestricted: Boolean, actions: OnboardingActions) {
    StepHeader(R.string.onboarding_service_title, R.string.onboarding_service_body)
    if (explainRestricted) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.onboarding_restricted_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.onboarding_restricted_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = actions.onOpenAppInfo) { Text(stringResource(R.string.action_open_app_info)) }
            }
        }
    }
    Button(onClick = actions.onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_turn_on))
    }
    Tip(R.string.onboarding_service_tip)
}

@Composable
private fun NotificationsStep(state: MainUiState, actions: OnboardingActions) {
    StepHeader(R.string.onboarding_notifications_title, R.string.onboarding_notifications_body)
    Button(onClick = actions.onAllowNotifications, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_allow_notifications))
    }
    Tip(R.string.onboarding_notifications_tip)
}

@Composable
private fun BatteryStep(state: MainUiState, actions: OnboardingActions) {
    StepHeader(R.string.onboarding_battery_title, R.string.onboarding_battery_body)
    Button(onClick = actions.onOpenBattery, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_set_unrestricted))
    }
    TextButton(onClick = actions.onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_skip)) }
}

@Composable
private fun SideKeyStep(state: MainUiState, actions: OnboardingActions) {
    StepHeader(R.string.onboarding_sidekey_title, R.string.onboarding_sidekey_body)
    if (state.sideKeyWorked) {
        Done(R.string.onboarding_sidekey_done)
        Button(onClick = actions.onFinish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_finish)) }
    } else {
        Text(stringResource(R.string.onboarding_sidekey_try), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.onboarding_sidekey_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = actions.onFinish, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_skip))
        }
    }
}
