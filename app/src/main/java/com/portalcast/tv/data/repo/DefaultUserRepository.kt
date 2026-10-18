package com.portalcast.tv.data.repo

import com.portalcast.tv.data.model.Preferences
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.net.apiCall
import com.portalcast.tv.data.store.SessionSource

/**
 * Provider key, preferences and progress (contract §3.6, §6).
 *
 * @param session when given, caches are keyed to the signed-in session so a re-login
 *   never sees the previous user's preferences. Without it they live for the process.
 * @param clock injectable for the progress TTL in tests.
 */
class DefaultUserRepository(
    private val api: ApiService,
    private val session: SessionSource? = null,
    clock: () -> Long = System::currentTimeMillis,
) : UserRepository {

    private val providerKeyCache = SessionCache<String>(session, clock = clock)
    private val preferencesCache = SessionCache<Preferences>(session, clock = clock)
    private val progressCache = SessionCache<List<ProgressRecord>>(session, ttlMs = PROGRESS_TTL_MS, clock = clock)

    /** Drop everything cached (e.g. on a `config_changed` socket event). */
    fun clearCaches() {
        providerKeyCache.clear()
        preferencesCache.clear()
        progressCache.clear()
    }

    override suspend fun providerKey(): Result<String> = providerKeyCache.get {
        val result = apiCall(ApiPaths.PROVIDER_KEY) {
            api.providerKey().providerKey?.takeIf { it.isNotBlank() } ?: FALLBACK_PROVIDER_KEY
        }
        val error = result.exceptionOrNull()
        if (error is ApiError.Http && error.code == 404) Result.success(FALLBACK_PROVIDER_KEY) else result
    }

    override suspend fun preferences(forceRefresh: Boolean): Result<Preferences> = preferencesCache.get(forceRefresh) {
        apiCall(ApiPaths.PROFILE) { api.profile().preferences ?: Preferences() }
    }

    /** The server shallow-merges and echoes the whole preference object; that becomes the cache. */
    override suspend fun updatePreferences(patch: Preferences): Result<Preferences> =
        apiCall(ApiPaths.PREFERENCES) { api.updatePreferences(patch.toPatch()) }.map { response ->
            val merged = response.preferences ?: (preferencesCache.peek() ?: Preferences()).mergedWith(patch)
            preferencesCache.put(merged)
            merged
        }

    /** Cached for [PROGRESS_TTL_MS] like the webui, so a burst of callers shares one fetch. */
    override suspend fun progress(forceRefresh: Boolean): Result<List<ProgressRecord>> = progressCache.get(forceRefresh) {
        apiCall(ApiPaths.PROGRESS) { api.progress() }
    }

    override suspend fun saveProgress(record: ProgressRecord): Result<Unit> {
        progressCache.clear()
        return apiCall(ApiPaths.PROGRESS) { api.saveProgress(record) }.map { }
    }

    override suspend fun deleteProgress(mediaId: String): Result<Unit> {
        progressCache.clear()
        return apiCall(ApiPaths.PROGRESS) { api.deleteProgress(mediaId) }.map { }
    }

    override suspend fun continueWatching(): Result<List<ProgressRecord>> = progress().map { selectContinueWatching(it) }

    companion object {
        const val PROGRESS_TTL_MS = 4_000L
        /** What the webui assumes when `/api/v2/provider-key` does not exist (pre-2026-09-21 servers). */
        const val FALLBACK_PROVIDER_KEY = "stalker_unknown"

        /**
         * The webui's Continue Watching selection (ContinueWatching.tsx): rows that are
         * not completed and not live TV, newest `meta.timestamp` first, one per
         * `meta.id ?: meta.itemId ?: meta.mediaId ?: mediaId`, skipping ids that also
         * have a completed row.
         */
        fun selectContinueWatching(rows: List<ProgressRecord>): List<ProgressRecord> {
            val completedIds = rows.asSequence().filter { it.completed }.map { it.mediaId }.toHashSet()
            val seen = HashSet<String>()
            return rows.asSequence()
                .filter { !it.completed }
                .map { it to it.parsedMeta }
                .filter { (_, meta) -> meta?.type != "tv" }
                .sortedByDescending { (_, meta) -> meta?.timestamp ?: 0L }
                .filter { (row, meta) ->
                    val id = listOf(meta?.id, meta?.itemId, meta?.mediaId, row.mediaId).firstOrNull { !it.isNullOrBlank() }
                        ?: return@filter false
                    id !in completedIds && seen.add(id)
                }
                .map { it.first }
                .toList()
        }
    }
}

/** Client-side equivalent of the server's shallow merge: non-null fields of [patch] win. */
internal fun Preferences.mergedWith(patch: Preferences): Preferences = Preferences(
    preferredContentType = patch.preferredContentType ?: preferredContentType,
    favorites = patch.favorites ?: favorites,
    recentChannels = patch.recentChannels ?: recentChannels,
    videoFitMode = patch.videoFitMode ?: videoFitMode,
    lastSelectedCategory = patch.lastSelectedCategory ?: lastSelectedCategory,
    lastSelectedCategoryTitle = patch.lastSelectedCategoryTitle ?: lastSelectedCategoryTitle,
    lastSelectedTvGroup = patch.lastSelectedTvGroup ?: lastSelectedTvGroup,
    lastSelectedTvChannel = patch.lastSelectedTvChannel ?: lastSelectedTvChannel,
    recentCategories = patch.recentCategories ?: recentCategories,
    pinnedCategories = patch.pinnedCategories ?: pinnedCategories,
    categoryOrder = patch.categoryOrder ?: categoryOrder,
    recentSearches = patch.recentSearches ?: recentSearches,
)
