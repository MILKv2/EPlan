package dev.milkthe.qbitplan.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import dev.milkthe.qbitplan.R

fun Context.copyText(text: String, @StringRes confirmation: Int) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
    // Android 13+ confirms copies itself.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, confirmation, Toast.LENGTH_SHORT).show()
}
