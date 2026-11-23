package dev.milkthe.qbitplan.ui.common

import dev.milkthe.qbitplan.core.model.PeriodRange
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val PolishLocale: Locale = Locale.forLanguageTag("pl-PL")

private val DayTitle = DateTimeFormatter.ofPattern("EEEE, d MMMM", PolishLocale)
private val DayShort = DateTimeFormatter.ofPattern("EEE d.MM", PolishLocale)
private val DayOfWeekShort = DateTimeFormatter.ofPattern("EEE", PolishLocale)
private val Time = DateTimeFormatter.ofPattern("H:mm", PolishLocale)
private val DateTime = DateTimeFormatter.ofPattern("d.MM, H:mm", PolishLocale)

fun LocalDate.titleText(): String = DayTitle.format(this).capitalized()

fun LocalDate.shortText(): String = DayShort.format(this)

fun LocalDate.dayOfWeekText(): String = DayOfWeekShort.format(this).trimEnd('.').capitalized()

fun LocalTime.text(): String = Time.format(this)

fun Instant.text(zone: ZoneId = ZoneId.systemDefault()): String = DateTime.format(atZone(zone))

fun PeriodRange.text(): String = if (first == last) "$first" else "$first–$last"

/** "4:05" or "1:02:03". */
fun Duration.countdownText(): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun String.capitalized() = replaceFirstChar { it.titlecase(PolishLocale) }

/** Distinct values, most frequent first. */
fun List<String>.byFrequency(): List<String> =
    groupingBy { it }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key.lowercase(PolishLocale) })).map { it.key }
