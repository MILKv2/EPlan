package dev.milkthe.qbitplan.data

import dev.milkthe.qbitplan.core.update.AppRelease
import dev.milkthe.qbitplan.core.update.AppVersion
import dev.milkthe.qbitplan.core.update.ReleaseApi
import dev.milkthe.qbitplan.core.update.ReleaseParser
import dev.milkthe.qbitplan.core.update.ReleaseResponse
import dev.milkthe.qbitplan.data.local.CachedEntry
import dev.milkthe.qbitplan.data.local.ResponseCache
import dev.milkthe.qbitplan.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateStatus {
    data object NeverChecked : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val release: AppRelease) : UpdateStatus
    data object Failed : UpdateStatus
}

/**
 * Checks GitHub for a newer release. The response is cached like EduPage ones, so the
 * "new version" hint survives restarts and offline starts without asking again.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val api: ReleaseApi,
    private val cache: ResponseCache,
    private val installed: AppVersion,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val checkMutex = Mutex()
    private val checking = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)
    @Volatile private var lastAttempt: Instant? = null

    /** A release newer than the installed app, or `null`. */
    val available: StateFlow<AppRelease?> = cache.observe(KEY)
        // A 304 only moves fetchedAt; parse again only when the body changed.
        .map { it?.body }
        .distinctUntilChanged()
        .map { body -> body?.let(::parseOrNull)?.takeIf { it.version > installed } }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Lazily, null)

    val status: Flow<UpdateStatus> = combine(
        cache.observe(KEY).map { it != null }.distinctUntilChanged(), available, checking, failed,
    ) { checkedOnce, release, isChecking, isFailed ->
        when {
            isChecking -> UpdateStatus.Checking
            release != null -> UpdateStatus.Available(release)
            isFailed -> UpdateStatus.Failed
            checkedOnce -> UpdateStatus.UpToDate
            else -> UpdateStatus.NeverChecked
        }
    }

    /** On app start: at most every [CHECK_EVERY], and after a failure not again for [RETRY_AFTER]. */
    fun checkIfDue() {
        scope.launch {
            val now = clock.instant()
            if (lastAttempt?.plus(RETRY_AFTER)?.isAfter(now) == true) return@launch
            val last = cache.get(KEY)?.fetchedAt
            if (last == null || last.plus(CHECK_EVERY).isBefore(now)) check()
        }
    }

    fun checkNow() {
        scope.launch { check() }
    }

    suspend fun check(): Boolean = checkMutex.withLock {
        checking.value = true
        lastAttempt = clock.instant()
        try {
            val cached = cache.get(KEY)
            val now = clock.instant()
            when (val response = api.fetchLatest(etag = cached?.version)) {
                is ReleaseResponse.Fresh -> cache.put(CachedEntry(KEY, response.body, response.etag, now))
                ReleaseResponse.NotModified -> cached?.let { cache.put(it.copy(fetchedAt = now)) }
                ReleaseResponse.NoRelease -> cache.put(CachedEntry(KEY, "", null, now))
            }
            failed.value = false
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed.value = true
            false
        } finally {
            checking.value = false
        }
    }

    private fun parseOrNull(body: String): AppRelease? =
        if (body.isEmpty()) null else runCatching { ReleaseParser.parse(body) }.getOrNull()

    companion object {
        const val KEY = "github_latest_release"
        val CHECK_EVERY: Duration = Duration.ofHours(12)
        val RETRY_AFTER: Duration = Duration.ofHours(1)
    }
}
