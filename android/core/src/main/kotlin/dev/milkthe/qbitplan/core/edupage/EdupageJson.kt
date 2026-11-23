package dev.milkthe.qbitplan.core.edupage

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal object EdupageJson {

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Returns `r`, or throws for `{"e": …}`, `{"reload": true}` and `{"r": {"error": …}}`. */
    fun unwrap(raw: String): JsonElement {
        val root = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (e: SerializationException) {
            throw EdupageException("EduPage response is not JSON", e)
        } catch (e: IllegalArgumentException) {
            throw EdupageException("EduPage response is not a JSON object", e)
        }
        root["e"]?.let { throw EdupageException("EduPage error: ${it.textOrJson()}") }
        if (root.containsKey("reload")) throw EdupageException("EduPage asked for a reload (bad __gsh?)")
        val r = root["r"] ?: throw EdupageException("Missing \"r\" in EduPage response")
        (r as? JsonObject)?.get("error")?.let { throw EdupageException("EduPage error: ${it.textOrJson()}") }
        return r
    }

    private fun JsonElement.textOrJson(): String = (this as? JsonPrimitive)?.content ?: toString()
}
