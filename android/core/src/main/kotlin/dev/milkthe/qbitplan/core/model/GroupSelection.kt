package dev.milkthe.qbitplan.core.model

/**
 * A student's groups, by name. [skipped] holds the groups of a division the student attends
 * none of (religion and ethics are both optional), so that division's lessons are hidden.
 */
data class GroupSelection(val picked: Set<String>, val skipped: Set<String> = emptySet()) {
    val isEmpty: Boolean get() = picked.isEmpty() && skipped.isEmpty()
}
