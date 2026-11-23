package dev.milkthe.qbitplan.core

import java.io.File

/** Shared fixtures from the repo root; Gradle passes the path as "fixtures.dir". */
object Fixtures {
    val dir: File = File(
        System.getProperty("fixtures.dir")
            ?: error("fixtures.dir not set – run tests through Gradle"),
    )

    fun read(path: String): String = File(dir, path).readText(Charsets.UTF_8)

    val versions: String get() = read("ttviewer_2026.json")
    val regular189: String get() = read("regulartt_189.json")
    fun substitutions(date: String): String = read("substitutions/$date.json")

    fun allSubstitutionDays(): Map<String, String> =
        File(dir, "substitutions").listFiles { f -> f.extension == "json" }!!
            .sortedBy { it.name }
            .associate { it.nameWithoutExtension to it.readText(Charsets.UTF_8) }
}
