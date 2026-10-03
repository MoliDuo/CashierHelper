package pro.xiangyu.cashierhelper.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pro.xiangyu.cashierhelper.R
import pro.xiangyu.cashierhelper.images.ImageFiles
import pro.xiangyu.cashierhelper.notify.NotificationButton
import pro.xiangyu.cashierhelper.notify.NotificationText
import pro.xiangyu.cashierhelper.tasks.TaskProblem
import pro.xiangyu.cashierhelper.tasks.TaskRecord
import pro.xiangyu.cashierhelper.tasks.TaskState
import pro.xiangyu.cashierhelper.ui.LaunchReason
import pro.xiangyu.cashierhelper.ui.MainUiState
import pro.xiangyu.cashierhelper.ui.theme.statusColors
import pro.xiangyu.cashierhelper.update.UpdateFeed

@Composable
fun HomeScreen(
    state: MainUiState,
    imageFiles: ImageFiles,
    onOpenSettings: () -> Unit,
    onPickPhotos: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDeferUpdate: () -> Unit,
    onFixService: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixBattery: () -> Unit,
    onDismissReason: () -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val status = state.status
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_settings)) }
            }
        }
        state.offeredUpdate?.let { feed ->
            item { UpdateBanner(feed, onInstallUpdate, onDeferUpdate) }
        }
        state.launchReason?.let { reason ->
            item { ReasonBanner(reason, onDismissReason, onFixService, onOpenSettings) }
        }
        item {
            StatusCard(
                status = status,
                onFixConnection = onOpenSettings,
                onFixService = onFixService,
            )
        }
        if (status.reminders.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    status.reminders.forEach { reminder ->
                        when (reminder) {
                            Reminder.NOTIFICATIONS_OFF -> ReminderRow(
                                stringResource(R.string.reminder_notifications),
                                stringResource(R.string.action_turn_on),
                                onFixNotifications,
                            )
                            Reminder.BATTERY_RESTRICTED -> ReminderRow(
                                stringResource(R.string.reminder_battery),
                                stringResource(R.string.action_set),
                                onFixBattery,
                            )
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onPickPhotos, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_pick_photos))
            }
        }
        if (state.needsAction.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.section_needs_action)) }
            items(state.needsAction, key = { it.id }) { task ->
                TaskRow(task, imageFiles, onRetry, onDelete, onOpenSettings)
            }
        }
        if (state.inProgress.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.section_in_progress)) }
            items(state.inProgress, key = { it.id }) { task ->
                TaskRow(task, imageFiles, onRetry, onDelete, onOpenSettings)
            }
        }
    }
}

@Composable
private fun UpdateBanner(feed: UpdateFeed, onInstall: () -> Unit, onLater: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.update_banner_title, feed.versionName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            feed.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { notes ->
                Text(
                    notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 3,
                )
            }
            Row {
                Button(onClick = onInstall) { Text(stringResource(R.string.update_action_install)) }
                TextButton(onClick = onLater) { Text(stringResource(R.string.update_action_later)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ReasonBanner(
    reason: LaunchReason,
    onDismiss: () -> Unit,
    onFixService: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val text = when (reason) {
        LaunchReason.NOT_CONFIGURED -> R.string.reason_not_configured
        LaunchReason.KEY_UNREADABLE -> R.string.reason_key_unreadable
        LaunchReason.SERVICE_OFF -> R.string.reason_service_off
        LaunchReason.SERVICE_NOT_RUNNING -> R.string.reason_service_not_running
    }
    val serviceReason = reason == LaunchReason.SERVICE_OFF || reason == LaunchReason.SERVICE_NOT_RUNNING
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(text), color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = if (serviceReason) onFixService else onOpenSettings) {
                    Text(stringResource(R.string.action_fix))
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_dismiss))
            }
        }
    }
}

@Composable
private fun StatusCard(status: HomeStatus, onFixConnection: () -> Unit, onFixService: () -> Unit) {
    val problem = status.problem
    val container = if (problem == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (problem == null) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (problem == null) MaterialTheme.colorScheme.primary else MaterialTheme.statusColors.warning,
                    modifier = Modifier.size(28.dp),
                )
                Column {
                    Text(
                        stringResource(titleFor(problem)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(bodyFor(problem)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (problem != null) {
                val serviceProblem = problem == HomeProblem.SERVICE_OFF || problem == HomeProblem.SERVICE_NOT_RUNNING
                Button(onClick = if (serviceProblem) onFixService else onFixConnection) {
                    Text(stringResource(actionFor(problem)))
                }
            }
        }
    }
}

private fun titleFor(problem: HomeProblem?) = when (problem) {
    null -> R.string.status_ready_title
    HomeProblem.NOT_CONNECTED -> R.string.status_not_connected_title
    HomeProblem.KEY_UNREADABLE -> R.string.status_key_unreadable_title
    HomeProblem.KEY_REJECTED -> R.string.status_key_rejected_title
    HomeProblem.SERVICE_OFF -> R.string.status_service_off_title
    HomeProblem.SERVICE_NOT_RUNNING -> R.string.status_service_not_running_title
}

private fun bodyFor(problem: HomeProblem?) = when (problem) {
    null -> R.string.status_ready_body
    HomeProblem.NOT_CONNECTED -> R.string.status_not_connected_body
    HomeProblem.KEY_UNREADABLE -> R.string.status_key_unreadable_body
    HomeProblem.KEY_REJECTED -> R.string.status_key_rejected_body
    HomeProblem.SERVICE_OFF -> R.string.status_service_off_body
    HomeProblem.SERVICE_NOT_RUNNING -> R.string.status_service_not_running_body
}

private fun actionFor(problem: HomeProblem) = when (problem) {
    HomeProblem.NOT_CONNECTED -> R.string.action_connect
    HomeProblem.KEY_UNREADABLE, HomeProblem.KEY_REJECTED -> R.string.action_update_key
    HomeProblem.SERVICE_OFF -> R.string.action_turn_on
    HomeProblem.SERVICE_NOT_RUNNING -> R.string.action_fix
}

@Composable
private fun ReminderRow(text: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun TaskRow(
    task: TaskRecord,
    imageFiles: ImageFiles,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val content = remember(task) { NotificationText.forTask(task, System.currentTimeMillis()) }
    val detail = if (task.state == TaskState.QUEUED && task.uploadAttempts > 1) {
        stringResource(R.string.task_attempts, content.text, task.uploadAttempts)
    } else {
        content.text
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Thumbnail(task, imageFiles)
                Column(Modifier.weight(1f)) {
                    Text(content.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    task.state == TaskState.NEEDS_ACTION && task.problem == TaskProblem.IMAGES_MISSING -> Unit
                    content.button == NotificationButton.FIX -> Button(onClick = onOpenSettings) {
                        Text(stringResource(R.string.action_fix))
                    }
                    task.state == TaskState.NEEDS_ACTION -> Button(onClick = { onRetry(task.id) }) {
                        Text(stringResource(R.string.action_retry))
                    }
                    task.state == TaskState.QUEUED && task.uploadAttempts > 0 ->
                        OutlinedButton(onClick = { onRetry(task.id) }) { Text(stringResource(R.string.action_retry_now)) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onDelete(task.id) }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(task: TaskRecord, imageFiles: ImageFiles) {
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, task.id, task.state) {
        value = imageFiles.list(task.id).firstOrNull()?.let { ThumbnailLoader.load(it, 160) }
    }
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
