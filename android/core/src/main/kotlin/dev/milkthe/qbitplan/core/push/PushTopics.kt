package dev.milkthe.qbitplan.core.push

import java.text.Normalizer

/**
 * FCM topics the worker (worker/src/topics.js) pushes to when a day's substitutions change.
 * Both sides are tested against fixtures/topics.json.
 */
object PushTopics {

    fun forClass(classShort: String): String = "klasa-${slug(classShort)}"

    /** [fullName] as substitutions spell it, e.g. "Krystian Małeta". */
    fun forTeacher(fullName: String): String = "naucz-${slug(fullName)}"

    // Topic names allow only [a-zA-Z0-9-_.~%].
    private fun slug(text: String): String {
        val ascii = text.lowercase().map { POLISH[it] ?: it }.joinToString("")
        return Normalizer.normalize(ascii, Normalizer.Form.NFKD)
            .replace(COMBINING_MARKS, "")
            .replace(NOT_ALLOWED, "-")
            .trim('-')
    }

    private val POLISH = mapOf('ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n', 'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z')
    private val COMBINING_MARKS = Regex("[\\u0300-\\u036f]")
    private val NOT_ALLOWED = Regex("[^a-z0-9]+")
}
