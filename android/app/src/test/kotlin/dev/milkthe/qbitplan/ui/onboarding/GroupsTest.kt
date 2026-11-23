package dev.milkthe.qbitplan.ui.onboarding

import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.edupage.RegularTimetableParser
import dev.milkthe.qbitplan.data.fixture
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupsTest {

    private val tt = RegularTimetableParser.parse(fixture("regulartt_189.json"), "189")

    @Test
    fun `joined class shows only divisions its lessons use`() {
        val divisions = divisionsOf(tt, tt.classByShort("4dft")!!.id)
        assertEquals(
            listOf(listOf("D-automatyk", "F-informatyk"), listOf("niem.K", "ros.P"), listOf("ang1", "ang2"), listOf("Religia", "Etyka")),
            divisions.map { it.options },
        )
        assertEquals(R.string.division_profession, divisions.first().label)
    }
}
