package dev.milkthe.qbitplan.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.milkthe.qbitplan.MainActivity
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.data.prefs.ThemeMode
import dev.milkthe.qbitplan.ui.common.ButtonVariant
import dev.milkthe.qbitplan.ui.common.QbitButton
import dev.milkthe.qbitplan.ui.common.StateMessage
import dev.milkthe.qbitplan.ui.theme.AppTheme
import dev.milkthe.qbitplan.ui.theme.QbitTheme

/** Shown by [CrashHandler] after a crash. Uses no data or preferences, so it can't crash the same way. */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val details = intent.getStringExtra(EXTRA_DETAILS).orEmpty()
        setContent {
            AppTheme(ThemeMode.SYSTEM) {
                Surface(Modifier.fillMaxSize(), color = QbitTheme.colors.background) {
                    CrashScreen(onRestart = ::restart, onCopy = { copy(details) })
                }
            }
        }
    }

    private fun restart() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun copy(details: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), details))
        // Android 13+ confirms copies itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_DETAILS = "details"
    }
}

@Composable
fun CrashScreen(onRestart: () -> Unit, onCopy: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            StateMessage(
                icon = Icons.Outlined.ErrorOutline,
                tone = QbitTheme.colors.danger,
                title = stringResource(R.string.crash_title),
                text = stringResource(R.string.crash_text),
            )
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            QbitButton(stringResource(R.string.crash_restart), onRestart, Modifier.fillMaxWidth(), icon = Icons.Outlined.Refresh)
            QbitButton(stringResource(R.string.crash_copy), onCopy, Modifier.fillMaxWidth(), variant = ButtonVariant.Ghost, icon = Icons.Outlined.ContentCopy)
        }
    }
}
