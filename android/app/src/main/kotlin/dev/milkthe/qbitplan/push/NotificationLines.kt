package dev.milkthe.qbitplan.push

import android.os.Bundle
import dev.milkthe.qbitplan.core.model.PeriodRange

/** A notification line; [periods] `null` for the "something changed" notice. */
data class NotificationLine(val periods: PeriodRange?, val text: String)

/**
 * Lines of a day's notification still on screen, plus a newer push's [added] lines.
 * A newer line replaces older ones about the same periods, so a lesson changed twice reads as
 * its latest state. An older "something changed" notice always goes: a newer push either repeats
 * it or brings the details, since the cached day it was compared against stayed the same.
 */
fun mergeLines(shown: List<NotificationLine>, added: List<NotificationLine>): List<NotificationLine> {
    val kept = shown.filter { old ->
        val periods = old.periods ?: return@filter false
        added.none { it.periods?.overlaps(periods) == true }
    }
    return (kept + added).sortedWith(compareBy(nullsLast()) { it.periods?.first })
}

private const val EXTRA_TEXTS = "dev.milkthe.qbitplan.LINE_TEXTS"
private const val EXTRA_FIRSTS = "dev.milkthe.qbitplan.LINE_FIRSTS"
private const val EXTRA_LASTS = "dev.milkthe.qbitplan.LINE_LASTS"
private const val NO_PERIOD = -1

// The inbox style keeps only the lines it shows, so all of them travel in extras too.
fun List<NotificationLine>.toExtras(): Bundle = Bundle().apply {
    putStringArray(EXTRA_TEXTS, map { it.text }.toTypedArray())
    putIntArray(EXTRA_FIRSTS, map { it.periods?.first ?: NO_PERIOD }.toIntArray())
    putIntArray(EXTRA_LASTS, map { it.periods?.last ?: NO_PERIOD }.toIntArray())
}

fun Bundle.notificationLines(): List<NotificationLine> {
    val texts = getStringArray(EXTRA_TEXTS) ?: return emptyList()
    val firsts = getIntArray(EXTRA_FIRSTS) ?: return emptyList()
    val lasts = getIntArray(EXTRA_LASTS) ?: return emptyList()
    if (firsts.size != texts.size || lasts.size != texts.size) return emptyList()
    return texts.indices.map { i ->
        NotificationLine(if (firsts[i] == NO_PERIOD) null else PeriodRange(firsts[i], lasts[i]), texts[i])
    }
}
