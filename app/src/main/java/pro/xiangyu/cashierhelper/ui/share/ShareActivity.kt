package pro.xiangyu.cashierhelper.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import pro.xiangyu.cashierhelper.CashierHelperApplication
import pro.xiangyu.cashierhelper.R
import pro.xiangyu.cashierhelper.tasks.SharedImageSubmitter
import pro.xiangyu.cashierhelper.tasks.TaskSource
import pro.xiangyu.cashierhelper.ui.theme.CashierTheme

/**
 * Receives images from the system share sheet. Like the capture trigger it lives in
 * the trigger task (see the manifest) and leaves as soon as the images are read.
 */
class ShareActivity : ComponentActivity() {
    private var asking by mutableStateOf<List<Uri>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = sharedUris(intent)
        if (uris.isEmpty()) {
            finish()
            return
        }
        if (SharedImageSubmitter.asksHowToGroup(uris.size)) {
            asking = uris
        } else {
            submit(uris, together = false)
        }

        setContent {
            CashierTheme {
                asking?.let { pending ->
                    GroupChoiceDialog(
                        count = pending.size,
                        onTogether = { asking = null; submit(pending, together = true) },
                        onSeparate = { asking = null; submit(pending, together = false) },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun submit(uris: List<Uri>, together: Boolean) {
        val graph = (application as CashierHelperApplication).graph
        // Reading must finish while this activity is alive; storing may outlive it.
        val submission = graph.appScope.async {
            SharedImageSubmitter(graph.imageReader, graph.intake).submit(TaskSource.SHARE, uris, together)
        }
        lifecycleScope.launch {
            val result = submission.await()
            val message = when {
                result.started == 0 -> getString(R.string.share_unreadable)
                result.unreadable > 0 -> getString(R.string.share_started_partial, result.unreadable)
                else -> getString(R.string.share_started)
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedUris(intent: Intent): List<Uri> {
        val found = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                },
            )
            Intent.ACTION_SEND_MULTIPLE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                } else {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                }
            else -> emptyList()
        }
        return found.take(SharedImageSubmitter.MAX_SELECTION)
    }
}
