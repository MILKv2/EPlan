package dev.milkthe.qbitplan.core.edupage

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Wspólna konfiguracja JSON i rozpakowanie koperty `{"r": …}`. */
internal object EdupageJson {

    val json = Json {
        ignoreUnknownKeys = true // EduPage wysyła dużo pól, bierzemy tylko potrzebne
        coerceInputValues = true // null w polu z wartością domyślną → domyślna
        explicitNulls = false
    }

    /**
     * Zwraca zawartość `r` albo rzuca [EdupageException] dla
     * `{"e": …}`, `{"reload": true}` i `{"r": {"error": …}}`.
     */
    fun unwrap(raw: String): JsonElement {
        val root = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (e: SerializationException) {
            throw EdupageException("Odpowiedź EduPage to nie JSON", e)
        } catch (e: IllegalArgumentException) {
            throw EdupageException("Odpowiedź EduPage to nie obiekt JSON", e)
        }
        root["e"]?.let { throw EdupageException("Błąd EduPage: ${it.textOrJson()}") }
        if (root.containsKey("reload")) throw EdupageException("EduPage żąda przeładowania (zły __gsh?)")
        val r = root["r"] ?: throw EdupageException("Brak pola \"r\" w odpowiedzi EduPage")
        (r as? JsonObject)?.get("error")?.let { throw EdupageException("Błąd EduPage: ${it.textOrJson()}") }
        return r
    }

    private fun JsonElement.textOrJson(): String = (this as? JsonPrimitive)?.content ?: toString()
}
