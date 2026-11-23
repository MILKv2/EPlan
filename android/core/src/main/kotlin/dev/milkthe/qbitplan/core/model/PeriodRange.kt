package dev.milkthe.qbitplan.core.model

/**
 * Zakres kolejnych lekcji (numerów z tabeli `periods`), np. 3-4.
 * Dla pojedynczej lekcji `first == last`.
 */
data class PeriodRange(val first: Int, val last: Int) {
    init {
        require(first <= last) { "Niepoprawny zakres lekcji: $first-$last" }
    }

    /** Pozwala pisać `5 in range` (słowo `operator` włącza tę składnię). */
    operator fun contains(period: Int): Boolean = period in first..last

    fun overlaps(other: PeriodRange): Boolean = first <= other.last && other.first <= last

    val count: Int get() = last - first + 1

    override fun toString(): String = if (first == last) "$first" else "$first-$last"

    // companion object = "statyczne" elementy klasy: PeriodRange.single(4)
    companion object {
        fun single(period: Int) = PeriodRange(period, period)
    }
}
