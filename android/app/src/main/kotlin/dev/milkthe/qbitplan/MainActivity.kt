package dev.milkthe.qbitplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.milkthe.qbitplan.crash.CrashHandler
import dev.milkthe.qbitplan.data.prefs.ThemeMode
import dev.milkthe.qbitplan.ui.navigation.MainNavigation
import dev.milkthe.qbitplan.ui.onboarding.OnboardingScreen
import dev.milkthe.qbitplan.ui.theme.AppTheme
import dev.milkthe.qbitplan.ui.theme.QbitTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Here rather than in QbitApp, so tests that don't start MainActivity keep the default handler.
        CrashHandler.install(application)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
            AppTheme(state?.theme ?: ThemeMode.SYSTEM) {
                Surface(Modifier.fillMaxSize(), color = QbitTheme.colors.background) {
                    when (state?.onboarded) {
                        null -> Unit
                        false -> OnboardingScreen()
                        true -> MainNavigation()
                    }
                }
            }
        }
    }
}
