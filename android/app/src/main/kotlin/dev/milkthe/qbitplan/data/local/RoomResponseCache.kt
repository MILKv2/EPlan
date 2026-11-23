package dev.milkthe.qbitplan.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

class RoomResponseCache @Inject constructor(
    private val dao: CachedResponseDao,
) : ResponseCache {

    override fun observe(key: String): Flow<CachedEntry?> = dao.observe(key).map { it?.toEntry() }

    override suspend fun get(key: String): CachedEntry? = dao.get(key)?.toEntry()

    override suspend fun put(entry: CachedEntry) = dao.upsert(
        CachedResponseEntity(
            key = entry.key,
            gzippedBody = gzip(entry.body),
            version = entry.version,
            fetchedAt = entry.fetchedAt.toEpochMilli(),
        ),
    )

    override suspend fun deleteWithPrefixBelow(prefix: String, keyBelow: String) =
        dao.deleteWithPrefixBelow(prefix, keyBelow)

    private fun CachedResponseEntity.toEntry() = CachedEntry(
        key = key,
        body = gunzip(gzippedBody),
        version = version,
        fetchedAt = Instant.ofEpochMilli(fetchedAt),
    )

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
}
