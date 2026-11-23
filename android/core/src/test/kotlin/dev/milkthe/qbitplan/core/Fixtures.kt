package dev.milkthe.qbitplan.core

import java.io.File

/**
 * Dostęp do wspólnych fixtures z katalogu repo (`/fixtures`).
 * Ścieżkę ustawia Gradle (właściwość systemowa "fixtures.dir").
 */
object Fixtures {
    val dir: File = File(
        System.getProperty("fixtures.dir")
            ?: error("Brak właściwości fixtures.dir – uruchamiaj testy przez Gradle"),
    )

    fun read(path: String): String = File(dir, path).readText(Charsets.UTF_8)

    val versions: String get() = read("ttviewer_2026.json")
    val regular189: String get() = read("regulartt_189.json")
    fun substitutions(date: String): String = read("substitutions/$date.json")

    /** Wszystkie dni zastępstw z fixtures: data → surowa odpowiedź. */
    fun allSubstitutionDays(): Map<String, String> =
        File(dir, "substitutions").listFiles { f -> f.extension == "json" }!!
            .sortedBy { it.name }
            .associate { it.nameWithoutExtension to it.readText(Charsets.UTF_8) }
}
