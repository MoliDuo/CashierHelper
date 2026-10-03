package pro.xiangyu.cashierhelper.ui.share

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import pro.xiangyu.cashierhelper.R

/** Asked when several images arrive at once: are they one bill or several? Tapping outside cancels. */
@Composable
fun GroupChoiceDialog(count: Int, onTogether: () -> Unit, onSeparate: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(pluralStringResource(R.plurals.share_group_title, count, count)) },
        text = { Text(stringResource(R.string.share_group_body)) },
        confirmButton = { TextButton(onClick = onTogether) { Text(stringResource(R.string.share_group_together)) } },
        dismissButton = { TextButton(onClick = onSeparate) { Text(stringResource(R.string.share_group_separate)) } },
    )
}
