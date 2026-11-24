package dev.milkthe.qbitplan.core.push

import dev.milkthe.qbitplan.core.Fixtures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class PushTopicsTest {

    @Test
    fun `topic names match the shared vectors (the worker checks the same file)`() {
        val vectors = Json.parseToJsonElement(Fixtures.read("topics.json")).jsonArray.map { it.jsonObject }
        for (vector in vectors) {
            val name = vector.getValue("name").jsonPrimitive.content
            val expected = vector.getValue("topic").jsonPrimitive.content
            val actual = if (vector.getValue("kind").jsonPrimitive.content == "class") PushTopics.forClass(name) else PushTopics.forTeacher(name)
            assertEquals(expected, actual, name)
        }
    }
}
