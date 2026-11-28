package dev.milkthe.qbitplan.ui.onboarding

import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.model.GroupSelection
import dev.milkthe.qbitplan.core.edupage.RegularTimetableParser
import dev.milkthe.qbitplan.data.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `religion is optional and starts on religion, other divisions default to all`() {
        val divisions = divisionsOf(tt, tt.classByShort("4dft")!!.id)
        val religion = divisions.single { it.label == R.string.division_religion }
        assertTrue(religion.optional)
        assertEquals("Religia", religion.default)
        assertTrue(divisions.filter { it !== religion }.none { it.optional })

        val fresh = Selection(classId = "x")
        assertEquals("Religia", fresh.pickIn(religion))
        assertNull(fresh.pickIn(divisions.first()))
        assertEquals(GroupSelection(setOf("Religia")), fresh.groupSelection(divisions))
    }

    @Test
    fun `skipping religion saves both groups as skipped`() {
        val divisions = divisionsOf(tt, tt.classByShort("4dft")!!.id)
        val religion = divisions.single { it.optional }
        val sel = Selection(groups = mapOf(divisions.first().id to "F-informatyk"), skippedDivisions = setOf(religion.id))
        assertNull(sel.pickIn(religion))
        assertEquals(GroupSelection(setOf("F-informatyk"), skipped = setOf("Religia", "Etyka")), sel.groupSelection(divisions))
        assertFalse(sel.groupSelection(divisions).isEmpty)
    }

    @Test
    fun `every division used in the timetable gets a label`() {
        val unlabelled = tt.classes.values.flatMap { cls -> divisionsOf(tt, cls.id).filter { it.label == null }.map { "${cls.short}: ${it.options}" } }
        assertEquals(emptyList<String>(), unlabelled)
    }
}
