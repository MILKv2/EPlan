package dev.milkthe.qbitplan.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.milkthe.qbitplan.BuildConfig
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.ui.common.AppScaffold
import dev.milkthe.qbitplan.ui.common.SectionCard
import dev.milkthe.qbitplan.ui.theme.QbitTheme

private const val AUTHOR_URL = "https://milkthe.dev"
private const val REPO_URL = "https://github.com/MILKv2/QbitPlan"

@Composable
fun AboutScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
    val uri = LocalUriHandler.current
    AppScaffold(title = stringResource(R.string.settings_about), onBack = onBack) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = QbitTheme.colors.muted)
            SectionCard(stringResource(R.string.about_unofficial_title)) {
                Text(stringResource(R.string.about_unofficial), style = MaterialTheme.typography.bodyMedium)
            }
            SectionCard(stringResource(R.string.about_author)) {
                Link(stringResource(R.string.about_author_value)) { uri.openUri(AUTHOR_URL) }
                Link(stringResource(R.string.about_source)) { uri.openUri(REPO_URL) }
            }
            SectionCard(stringResource(R.string.about_license)) {
                Text(stringResource(R.string.about_license_value), style = MaterialTheme.typography.bodyMedium)
                Link(stringResource(R.string.about_oss_licenses), onLicenses)
            }
        }
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = QbitTheme.colors.primary.accent,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

private data class OssLibrary(val name: String, val license: String)

private val OssLibraries = listOf(
    OssLibrary("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0"),
    OssLibrary("AndroidX, Jetpack Compose, Material 3, Room, DataStore, Navigation", "Apache License 2.0"),
    OssLibrary("Material Icons", "Apache License 2.0"),
    OssLibrary("Dagger / Hilt", "Apache License 2.0"),
    OssLibrary("OkHttp", "Apache License 2.0"),
    OssLibrary("jsoup", "MIT License"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    AppScaffold(title = stringResource(R.string.about_oss_licenses), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(OssLibraries, key = { it.name }) { lib ->
                SectionCard(lib.license) { Text(lib.name, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
