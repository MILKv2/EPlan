package dev.milkthe.qbitplan.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.ui.common.BackHeader
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.common.PolishLocale
import dev.milkthe.qbitplan.ui.common.RadioRow
import dev.milkthe.qbitplan.ui.common.SettingsHeader
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import java.time.format.DateTimeFormatter

private val ValidFrom = DateTimeFormatter.ofPattern("d.MM.yyyy", PolishLocale)

/** Automatic (by date) or one published version, pinned until a newer one appears. */
@Composable
fun VersionScreen(onBack: () -> Unit, vm: VersionViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackHeader(stringResource(R.string.version_title), onBack)
        if (state.loading) {
            LoadingBox()
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            RadioRow(
                text = stringResource(R.string.version_auto),
                selected = state.pinned == null,
                onClick = vm::chooseAutomatic,
                subtitle = state.automaticName?.let { stringResource(R.string.version_auto_desc, it) }
                    ?: stringResource(R.string.version_auto_desc_empty),
            )
            SettingsHeader(stringResource(R.string.version_published))
            if (state.options.isEmpty()) {
                Hint(stringResource(R.string.version_empty))
            }
            state.options.forEach { option ->
                RadioRow(
                    text = option.name,
                    selected = state.pinned == option.ttNum,
                    onClick = { vm.choose(option.ttNum) },
                    subtitle = stringResource(R.string.version_from, ValidFrom.format(option.validFrom)),
                )
            }
            Hint(stringResource(R.string.version_hint))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = QbitTheme.colors.muted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
