package dev.milkthe.qbitplan.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.BuildConfig
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.update.AppRelease
import dev.milkthe.qbitplan.data.UpdateStatus
import dev.milkthe.qbitplan.ui.common.AppIcon
import dev.milkthe.qbitplan.ui.common.BackHeader
import dev.milkthe.qbitplan.ui.common.Chevron
import dev.milkthe.qbitplan.ui.common.SettingsRow
import dev.milkthe.qbitplan.ui.common.copyText
import dev.milkthe.qbitplan.ui.theme.QbitTheme

private const val AUTHOR_URL = "https://milkthe.dev"
private const val REPO_URL = "https://github.com/MILKv2/EPlan"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val PRIVACY_URL = "https://github.com/MILKv2/EPlan/blob/main/docs/privacy.md"

@Composable
fun AboutScreen(onBack: () -> Unit, onLicenses: () -> Unit, vm: AboutViewModel = hiltViewModel()) {
    val uri = LocalUriHandler.current
    val update by vm.update.collectAsStateWithLifecycle()
    val colors = QbitTheme.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackHeader(stringResource(R.string.settings_about), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppIcon(size = 72.dp, cornerRadius = 22.dp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            Text(
                stringResource(R.string.about_unofficial),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceSecondary).padding(horizontal = 16.dp, vertical = 14.dp),
            )
            if (BuildConfig.SELF_UPDATE) UpdateRow(update, onCheck = vm::checkNow, onDownload = { uri.openUri(it.apkUrl ?: it.pageUrl) })
            val context = LocalContext.current
            val discord = stringResource(R.string.contact_discord)
            SettingsRow(
                stringResource(R.string.about_report),
                stringResource(R.string.about_report_value, discord),
                onClick = { context.copyText(discord, R.string.about_report_copied) },
                leading = { Icon(Icons.Outlined.BugReport, contentDescription = null, tint = colors.muted) },
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
            }
            LinkRow(Icons.Outlined.Person, stringResource(R.string.about_author), stringResource(R.string.about_author_value)) { uri.openUri(AUTHOR_URL) }
            LinkRow(Icons.Outlined.Code, stringResource(R.string.about_source), stringResource(R.string.about_source_value)) { uri.openUri(REPO_URL) }
            LinkRow(Icons.Outlined.Description, stringResource(R.string.about_license), stringResource(R.string.about_license_value)) { uri.openUri(LICENSE_URL) }
            LinkRow(Icons.Outlined.PrivacyTip, stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_value)) { uri.openUri(PRIVACY_URL) }
            SettingsRow(stringResource(R.string.about_oss_licenses), stringResource(R.string.about_oss_desc), onClick = onLicenses, leading = { Icon(Icons.AutoMirrored.Outlined.LibraryBooks, contentDescription = null, tint = colors.muted) }) { Chevron() }
        }
    }
}

@Composable
private fun UpdateRow(status: UpdateStatus, onCheck: () -> Unit, onDownload: (AppRelease) -> Unit) {
    val colors = QbitTheme.colors
    val subtitle = when (status) {
        UpdateStatus.NeverChecked -> stringResource(R.string.about_update_never)
        UpdateStatus.Checking -> stringResource(R.string.about_update_checking)
        UpdateStatus.UpToDate -> stringResource(R.string.about_update_latest)
        is UpdateStatus.Available -> stringResource(R.string.about_update_available, status.release.version.toString())
        UpdateStatus.Failed -> stringResource(R.string.about_update_failed)
    }
    SettingsRow(
        title = stringResource(R.string.about_updates),
        subtitle = subtitle,
        onClick = { if (status is UpdateStatus.Available) onDownload(status.release) else onCheck() },
        enabled = status != UpdateStatus.Checking,
        leading = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = if (status is UpdateStatus.Available) colors.accent.softForeground else colors.muted) },
    ) {
        when (status) {
            UpdateStatus.Checking -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.muted)
            is UpdateStatus.Available -> Icon(Icons.Outlined.Download, contentDescription = null, tint = colors.accent.softForeground, modifier = Modifier.size(20.dp))
            else -> Icon(Icons.Outlined.Refresh, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
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
