package dev.milkthe.qbitplan.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.milkthe.qbitplan.BuildConfig
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.ui.common.BackHeader
import dev.milkthe.qbitplan.ui.common.Chevron
import dev.milkthe.qbitplan.ui.common.SettingsRow
import dev.milkthe.qbitplan.ui.theme.QbitTheme

private const val AUTHOR_URL = "https://milkthe.dev"
private const val REPO_URL = "https://github.com/MILKv2/QbitPlan"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@Composable
fun AboutScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
    val uri = LocalUriHandler.current
    val colors = QbitTheme.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackHeader(stringResource(R.string.settings_about), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(colors.accent.solid), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            Text(
                stringResource(R.string.about_unofficial),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceSecondary).padding(horizontal = 16.dp, vertical = 14.dp),
            )
            LinkRow(Icons.Outlined.Person, stringResource(R.string.about_author), stringResource(R.string.about_author_value)) { uri.openUri(AUTHOR_URL) }
            LinkRow(Icons.Outlined.Code, stringResource(R.string.about_source), stringResource(R.string.about_source_value)) { uri.openUri(REPO_URL) }
            LinkRow(Icons.Outlined.Description, stringResource(R.string.about_license), stringResource(R.string.about_license_value)) { uri.openUri(LICENSE_URL) }
            SettingsRow(stringResource(R.string.about_oss_licenses), stringResource(R.string.about_oss_desc), onClick = onLicenses, leading = { Icon(Icons.AutoMirrored.Outlined.LibraryBooks, contentDescription = null, tint = colors.muted) }) { Chevron() }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    SettingsRow(title, subtitle, onClick = onClick, leading = { Icon(icon, contentDescription = null, tint = QbitTheme.colors.muted) }) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = QbitTheme.colors.muted, modifier = Modifier.size(18.dp))
    }
}

private data class OssLibrary(val name: String, val license: String)

private val OssLibraries = listOf(
    OssLibrary("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0"),
    OssLibrary("AndroidX, Jetpack Compose, Material 3, Room, DataStore, Navigation", "Apache License 2.0"),
    OssLibrary("Material Icons", "Apache License 2.0"),
    OssLibrary("Dagger / Hilt", "Apache License 2.0"),
    OssLibrary("OkHttp", "Apache License 2.0"),
    OssLibrary("jsoup", "MIT License"),
    OssLibrary("Inter (Rasmus Andersson)", "SIL Open Font License 1.1"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackHeader(stringResource(R.string.about_oss_licenses), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            OssLibraries.forEach { SettingsRow(it.name, it.license) }
        }
    }
}
