package com.eclipsia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

/** Container for data fetched during MALSync requests */
data class MalSyncData(
    val title: String?,
    val animepaheUrl: String?,
    val aniId: Int?,
    val malId: Int?,
    val episode: Int?,
    val year: Int?,
    val origin: String,
    val animepaheTitle: String?,
)

/** * Defines a provider and its execution logic for Standard, Anime, and MALSync data.
 * The `CineStreamExtractors.` receiver allows direct access to internal scraping functions.
 */
data class ProviderDef(
    val key: String,
    val displayName: String,
    val isTorrent: Boolean = false,
    val executeStandard: (suspend CineStreamExtractors.(res: AllLoadLinksData, subCb: (SubtitleFile) -> Unit, cb: (ExtractorLink) -> Unit) -> Unit)? = null,
    val executeAnime: (suspend CineStreamExtractors.(res: AllLoadLinksData, subCb: (SubtitleFile) -> Unit, cb: (ExtractorLink) -> Unit) -> Unit)? = null,
    val executeMalSync: (suspend CineStreamExtractors.(data: MalSyncData, subCb: (SubtitleFile) -> Unit, cb: (ExtractorLink) -> Unit) -> Unit)? = null
)

object ProviderRegistry {

    val builtInProviders = listOf(
        // ── Subtitles ────────────────────────────
        ProviderDef(
            key = "p_notorrent", displayName = "NoTorrent",
            executeStandard = { res, subCb, cb -> invokeStremioStreams("NoTorrent", notorrentAPI, res.imdbId, res.season, res.episode, subCb, cb) }
        ),
        ProviderDef(
            key = "p_wyziesubs", displayName = "WYZIESubs",
            executeStandard = { res, subCb, _ -> invokeWYZIESubs(res.imdbId, res.season, res.episode, subCb) },
            executeAnime = { res, subCb, _ -> invokeWYZIESubs(res.imdbId, res.imdbSeason, res.imdbEpisode, subCb) }
        ),

        // ── Direct HTTP Providers ─────────────────────────────────
        ProviderDef(
            key = "p_videasy", displayName = "Videasy",
            executeStandard = { res, subCb, cb -> invokeVideasy(res.title, res.tmdbId, res.imdbId, res.year, res.season, res.episode, subCb, cb) }
        ),
        ProviderDef(
            key = "p_vidlink", displayName = "Vidlink",
            executeStandard = { res, subCb, cb -> invokeVidlink(res.tmdbId, res.season, res.episode, subCb, cb) },
        ),
        ProviderDef(
            key = "p_vidfastpro", displayName = "VidFastPro",
            executeStandard = { res, subCb, cb -> invokeVidFastPro(res.tmdbId, res.season, res.episode, subCb, cb) }
        ),
        ProviderDef(
            key = "p_reanime", displayName = "Reanime",
            executeAnime = { res, subCb, cb -> invokeReanime(res.anilistId, res.episode, subCb, cb) }
        ),
        ProviderDef(
            key = "p_netflix", displayName = "Netflix",
            executeStandard = { res, subCb, cb -> invokeNetmirror("Netflix", "nf", res.title, res.year, res.season, res.episode, subCb, cb) },
            executeAnime = { res, subCb, cb -> invokeNetmirror("Netflix", "nf", res.imdbTitle, res.imdbYear, res.imdbSeason, res.imdbEpisode, subCb, cb) }
        ),
        ProviderDef(
            key = "p_primevideo", displayName = "Prime Video",
            executeStandard = { res, subCb, cb -> invokeNetmirror("PrimeVideo", "pv", res.title, res.year, res.season, res.episode, subCb, cb) },
            executeAnime = { res, subCb, cb -> invokeNetmirror("PrimeVideo", "pv", res.imdbTitle, res.imdbYear, res.imdbSeason, res.imdbEpisode, subCb, cb) }
        ),
        ProviderDef(
            key = "p_disney", displayName = "Hotstar",
            executeStandard = { res, subCb, cb -> invokeNetmirror("Hotstar", "hs", res.title, res.year, res.season, res.episode, subCb, cb) },
            executeAnime = { res, subCb, cb -> invokeNetmirror("Hotstar", "hs", res.imdbTitle, res.imdbYear, res.imdbSeason, res.imdbEpisode, subCb, cb) }
        ),

    // Dynamically provided to Settings.kt
    val keys get() = builtInProviders.map { it.key }
    val namesMap get() = builtInProviders.associate { it.key to it.displayName }
    val torrentKeys get() = builtInProviders.filter { it.isTorrent }.map { it.key }.toSet()
}
