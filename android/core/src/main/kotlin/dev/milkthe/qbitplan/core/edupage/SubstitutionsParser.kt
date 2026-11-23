package dev.milkthe.qbitplan.core.edupage

import dev.milkthe.qbitplan.core.model.AbsenceNote
import dev.milkthe.qbitplan.core.model.Change
import dev.milkthe.qbitplan.core.model.DaySubstitutions
import dev.milkthe.qbitplan.core.model.PeriodRange
import dev.milkthe.qbitplan.core.model.Substitution
import dev.milkthe.qbitplan.core.model.SubstitutionType
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Parses `getSubstViewerDayDataHtml`. Grammar: docs/edupage-api.md, section 3. */
object SubstitutionsParser {

    fun parse(date: LocalDate, raw: String): DaySubstitutions {
        val html = (EdupageJson.unwrap(raw) as? JsonPrimitive)?.content
            ?: throw EdupageException("Substitutions: \"r\" is not a string")
        return parseHtml(date, html)
    }

    fun parseHtml(date: LocalDate, html: String): DaySubstitutions {
        val doc = Jsoup.parseBodyFragment(html)
        val dayRoot = doc.selectFirst("div[data-date]")
            // No table: just a message, e.g. "Zastępstwa nie zostały jeszcze opublikowane."
            ?: return emptyDay(date, message = doc.text().clean().ifBlank { null })

        val absentTeachers = mutableListOf<AbsenceNote>()
        val absentClasses = mutableListOf<AbsenceNote>()
        val unavailableRooms = mutableListOf<AbsenceNote>()
        val otherNotes = mutableListOf<String>()

        for (span in dayRoot.select("> div > span")) {
            val text = span.text().clean()
            val label = text.substringBefore(':', missingDelimiterValue = "").trim().lowercase()
            val entries = text.substringAfter(':', missingDelimiterValue = "")
            when (label) {
                "nieobecni nauczyciele", "absent teachers" -> absentTeachers += parseAbsences(entries)
                "klasy nieobecne", "absent classes" -> absentClasses += parseAbsences(entries)
                "niedostępne sale szkolne", "unavailable classrooms" -> unavailableRooms += parseAbsences(entries)
                else -> if (text.isNotEmpty()) otherNotes += text
            }
        }

        val substitutions = mutableListOf<Substitution>()
        var message: String? = null
        // One <tbody> per class; the class name is only in the first row (rowspan).
        for (tbody in dayRoot.select("table > tbody")) {
            var className = ""
            for (row in tbody.select("> tr")) {
                row.selectFirst("> td.header")?.let { className = it.text().clean() }
                val periodText = row.cellText("period")
                val what = row.selectFirst("> td.what > span")
                val info = row.selectFirst("> td.info > span")
                val whatText = what?.text()?.clean().orEmpty()
                val infoText = info?.text()?.clean().orEmpty()
                if (className.isEmpty() && periodText.isEmpty() && whatText.isEmpty()) {
                    if (infoText.isNotEmpty()) message = infoText
                    continue
                }
                substitutions += parseRow(className, periodText, what, info, whatText, infoText)
            }
        }

        return DaySubstitutions(
            date = date,
            absentTeachers = absentTeachers,
            absentClasses = absentClasses,
            unavailableClassrooms = unavailableRooms,
            otherNotes = otherNotes,
            substitutions = substitutions,
            message = message,
            generatedAt = parseGeneratedAt(doc.selectFirst("a[href*=asctimetables]")?.parent()?.text()),
        )
    }

    private fun parseRow(
        className: String,
        periodText: String,
        what: Element?,
        info: Element?,
        whatText: String,
        infoText: String,
    ): Substitution {
        // "what" column: [groups: ]subject
        val classAbsent = what?.selectFirst("img[src*=absent]") != null
        var groups = emptyList<String>()
        var subject: Change? = null
        if (!classAbsent && what != null) {
            val text = what.markedText()
            val colon = text.indexOf(": ")
            val strike = text.indexOf(STRIKE_START).let { if (it < 0) Int.MAX_VALUE else it }
            val subjectPart = if (colon in 0 until strike) {
                groups = text.substring(0, colon).splitTopLevel()
                text.substring(colon + 2)
            } else {
                text
            }
            subject = parseChange(subjectPart)
        }

        // "info" column: comma-separated, mostly labelled parts
        var teacher: Change? = null
        var classroom: Change? = null
        var cancelled = false
        var movedFrom: Int? = null
        var movedTo: Int? = null
        val withoutClasses = mutableListOf<String>()
        val unknown = mutableListOf<String>()

        for (part in info?.markedText().orEmpty().splitTopLevel()) {
            val labelMatch = LABELLED.matchEntire(part)
            val label = labelMatch?.groupValues?.get(1)?.trim()?.lowercase()
            val value = labelMatch?.groupValues?.get(2)?.trim().orEmpty()
            when {
                label in TEACHER_CHANGE_LABELS || label in TEACHER_LABELS -> teacher = parseChange(value)
                label in CLASSROOM_LABELS -> classroom = parseChange(value)
                label in MOVED_TO_LABELS -> movedTo = value.toIntOrNull()
                label in MOVED_FROM_LABELS -> movedFrom = value.toIntOrNull()
                label != null -> unknown += part.stripMarkers()
                part.equals("Anulowano", ignoreCase = true) || part.equals("Cancelled", ignoreCase = true) ->
                    cancelled = true
                WITHOUT_CLASS.matches(part) ->
                    withoutClasses += WITHOUT_CLASS.matchEntire(part)!!.groupValues[1].split(",").map { it.trim() }
                // A struck-out name alone = no substitute assigned.
                part.startsWith(STRIKE_START) && part.endsWith(STRIKE_END) -> teacher = parseChange(part)
                // Bare name, as in "X, Anulowano" / "X, Moved to period: 4".
                teacher == null && STRIKE_START !in part -> teacher = Change.unchanged(part)
                else -> unknown += part.stripMarkers()
            }
        }

        // Most specific first.
        val type = when {
            classAbsent -> SubstitutionType.CLASS_ABSENT
            cancelled -> SubstitutionType.CANCELLED
            movedTo != null -> SubstitutionType.MOVED_AWAY
            movedFrom != null -> SubstitutionType.MOVED_HERE
            teacher?.old != null && teacher.new == null -> SubstitutionType.NO_SUBSTITUTE
            else -> SubstitutionType.CHANGE
        }

        return Substitution(
            className = className,
            periods = parsePeriods(periodText),
            type = type,
            groups = groups,
            subject = subject,
            teacher = teacher,
            classroom = classroom,
            movedFromPeriod = movedFrom,
            movedToPeriod = movedTo,
            withoutClasses = withoutClasses,
            unknownParts = unknown,
            whatText = whatText,
            infoText = infoText,
        )
    }


    // <s>…</s> becomes marker chars so the rest can work on plain text
    // without losing what was struck out.
    private const val STRIKE_START = '\u0001'
    private const val STRIKE_END = '\u0002'

    private val LABELLED = Regex("^([^:\u0001(]+):\\s*(.*)$")
    private val WITHOUT_CLASS = Regex("^\\((?:Bez klasy|Without class)\\s+(.+)\\)$", RegexOption.IGNORE_CASE)
    private val PERIODS = Regex("^\\(?\\s*(\\d+)(?:\\s*-\\s*(\\d+))?\\s*\\)?$")
    private val ABSENCE = Regex("^(.*?)\\s*(?:\\((\\d+)(?:\\s*-\\s*(\\d+))?\\))?$")
    private val GENERATED_AT = Regex("(\\d{2}\\.\\d{2}\\.\\d{4} \\d{1,2}:\\d{2}:\\d{2})")
    private val GENERATED_AT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm:ss")

    // EduPage leaves some labels untranslated ("Moved to period"), so accept both languages.
    private val TEACHER_CHANGE_LABELS = setOf("zastępstwa", "zastępstwo", "substitution", "substitutions")
    private val TEACHER_LABELS = setOf("nauczyciel", "teacher")
    private val CLASSROOM_LABELS = setOf("zmień salę lekcyjną", "change classroom", "sala", "classroom")
    private val MOVED_TO_LABELS = setOf("moved to period", "przeniesiono na lekcję", "przeniesiona na lekcję")
    private val MOVED_FROM_LABELS = setOf("moved from period", "przeniesiono z lekcji", "przeniesiona z lekcji")

    private fun emptyDay(date: LocalDate, message: String?) = DaySubstitutions(
        date, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), message, null,
    )

    private fun Element.markedText(): String = buildString {
        for (node in childNodes()) {
            when {
                node is TextNode -> append(node.text())
                node is Element && node.tagName() == "s" -> append(STRIKE_START).append(node.text()).append(STRIKE_END)
                node is Element && node.tagName() != "img" -> append(node.markedText())
            }
        }
    }.clean()

    private fun String.stripMarkers(): String = replace(STRIKE_START.toString(), "").replace(STRIKE_END.toString(), "")

    /** "<s>A</s> ➔ B" → Change(A, B); "<s>A</s>" → Change(A, null); "A" → Change(A, A). */
    private fun parseChange(value: String): Change? {
        val v = value.trim()
        if (v.isEmpty()) return null
        val start = v.indexOf(STRIKE_START)
        val end = v.indexOf(STRIKE_END)
        if (start < 0 || end < start) return Change.unchanged(v)
        val old = v.substring(start + 1, end).trim()
        val new = v.substring(end + 1).replace("➔", "").replace("->", "").trim()
        return Change(old.ifEmpty { null }, new.ifEmpty { null })
    }

    /** "4", "3 - 4", "(4)", "(1 - 2)" → range; "Cały dzień" and empty → null. */
    private fun parsePeriods(text: String): PeriodRange? {
        val match = PERIODS.matchEntire(text.trim()) ?: return null
        val first = match.groupValues[1].toInt()
        val last = match.groupValues[2].toIntOrNull() ?: first
        return PeriodRange(minOf(first, last), maxOf(first, last))
    }

    private fun parseAbsences(text: String): List<AbsenceNote> =
        text.splitTopLevel().mapNotNull { entry ->
            val match = ABSENCE.matchEntire(entry.trim()) ?: return@mapNotNull null
            val name = match.groupValues[1].trim()
            if (name.isEmpty()) return@mapNotNull null
            val first = match.groupValues[2].toIntOrNull()
            val last = match.groupValues[3].toIntOrNull() ?: first
            AbsenceNote(name, if (first != null && last != null) PeriodRange(first, last) else null)
        }

    private fun parseGeneratedAt(footer: String?): LocalDateTime? {
        val stamp = footer?.let { GENERATED_AT.find(it)?.value } ?: return null
        return runCatching { LocalDateTime.parse(stamp, GENERATED_AT_FORMAT) }.getOrNull()
    }

    /** Splits on commas outside parentheses, so "(Bez klasy 1a, 2b)" stays whole. */
    private fun String.splitTopLevel(): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in this) {
            when {
                ch == '(' -> depth++
                ch == ')' -> depth = (depth - 1).coerceAtLeast(0)
            }
            if (ch == ',' && depth == 0) {
                parts += current.toString().trim()
                current.clear()
            } else {
                current.append(ch)
            }
        }
        parts += current.toString().trim()
        return parts.filter { it.isNotEmpty() }
    }

    private fun Element.cellText(cssClass: String): String = selectFirst("> td.$cssClass")?.text()?.clean().orEmpty()

    private fun String.clean(): String = replace(' ', ' ').replace(Regex(" {2,}"), " ").trim()
}
