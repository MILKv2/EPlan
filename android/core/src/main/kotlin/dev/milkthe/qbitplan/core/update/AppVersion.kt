package dev.milkthe.qbitplan.core.update

/** MAJOR.MINOR.PATCH, as in `android/version.properties` and the release tags (`v1.2.3`). */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""v?(\d+)\.(\d+)\.(\d+)""")

        /** `null` for anything that isn't a plain release version (e.g. `v1.2.3-beta`). */
        fun parse(text: String): AppVersion? = PATTERN.matchEntire(text.trim())?.destructured?.let { (major, minor, patch) ->
            AppVersion(major.toIntOrNull() ?: return null, minor.toIntOrNull() ?: return null, patch.toIntOrNull() ?: return null)
        }
    }
}
