package dev.milkthe.qbitplan.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Clock

/** Time source for UI that shows "now"; overridden in screenshot tests. */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.systemDefaultZone() }
