package dev.milkthe.qbitplan.core.model

/** Consecutive lesson numbers (from the `periods` table), inclusive. */
data class PeriodRange(val first: Int, val last: Int) {
    init {
        require(first <= last) { "Invalid period range: $first-$last" }
    }

    operator fun contains(period: Int): Boolean = period in first..last

    fun overlaps(other: PeriodRange): Boolean = first <= other.last && other.first <= last

    val count: Int get() = last - first + 1

    override fun toString(): String = if (first == last) "$first" else "$first-$last"

    companion object {
        fun single(period: Int) = PeriodRange(period, period)
    }
}
