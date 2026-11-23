package dev.milkthe.qbitplan.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.model.Period
import dev.milkthe.qbitplan.core.schedule.BellState
import dev.milkthe.qbitplan.core.schedule.bellStateAt
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime

/** "Now" card with a bell countdown. Ticks once a second; only this composable recomposes. */
@Composable
fun NowBar(periods: List<Period>, lessons: List<LessonItem>, modifier: Modifier = Modifier) {
    val clock = LocalClock.current
    val now by produceState(LocalTime.now(clock), clock) {
        while (true) {
            value = LocalTime.now(clock)
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    val upcoming = lessons.filter { it.status.takesPlace }
    val state = bellStateAt(periods, now)
    val lastOwn = upcoming.maxOfOrNull { it.periods.last } ?: return

    val title: String
    val detail: String?
    val countdown: Duration
    var progress: Float? = null
    when (state) {
        is BellState.DuringLesson -> {
            if (state.period.number > lastOwn) return
            val lesson = upcoming.firstOrNull { state.period.number in it.periods }
            title = lesson?.let { stringResource(R.string.now_lesson, it.subject) } ?: stringResource(R.string.now_free_period)
            detail = lesson?.room
            countdown = state.untilBell(now)
            val total = Duration.between(state.period.start, state.period.end).seconds.toFloat()
            progress = (1f - countdown.seconds / total).coerceIn(0f, 1f)
        }
        is BellState.Break -> {
            if (state.next.number > lastOwn) return
            val next = upcoming.firstOrNull { state.next.number in it.periods }
            title = if (state.previous == null) stringResource(R.string.now_before_school) else stringResource(R.string.now_break)
            detail = next?.let { stringResource(R.string.now_next, it.subject) + (it.room?.let { r -> " · $r" } ?: "") }
            countdown = state.untilBell(now)
        }
        BellState.AfterSchool -> return
    }

    val tint = QbitTheme.colors.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QbitTheme.radius.medium))
            .background(tint.container)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = tint.content)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tint.content) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(countdown.countdownText(), style = MaterialTheme.typography.titleLarge, color = tint.content)
                Text(stringResource(R.string.now_until_bell), style = MaterialTheme.typography.labelSmall, color = tint.content)
            }
        }
        progress?.let {
            LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = tint.accent, trackColor = QbitTheme.colors.content1)
        }
    }
}
