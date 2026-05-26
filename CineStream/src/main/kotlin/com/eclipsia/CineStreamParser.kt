package com.eclipsia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

//year => for movie : release year for series : season 1 release year
//airedYear => for movie : release year for series : episode release year
//imdbTitle, imdbSeason, imdbEpisode, imdbYear => for kitsu providers

data class AllLoadLinksData(
    val title: String? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val anilistId: Int? = null,
    val malId: Int? = null,
    val kitsuId: String? = null,
    val year: Int? = null,
    val airedYear: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val isAnime: Boolean = false,
    val isBollywood: Boolean = false,
    val isAsian: Boolean = false,
    val isCartoon: Boolean = false,
    val originalTitle: String? = null,
    val imdbTitle: String? = null,
    val imdbSeason : Int? = null,
    val imdbEpisode : Int? = null,
    val imdbYear : Int? = null,
)

// Vidfast
data class VidfastServers(
    @param:JsonProperty("name") val name: String?,
    @param:JsonProperty("description") val description: String?,
    @param:JsonProperty("data") val data: String?
)

data class VidfastStreamResponse(
    val result: List<VidfastServers>
)

data class VidfastServersStreamRoot(
    val result: VidfastServer,
)

data class VidfastServer(
    @param:JsonProperty("url") val url: String?,
    @param:JsonProperty("tracks") val tracks: List<VidfastTrack>?,
    @param:JsonProperty("4kAvailable") val is4kAvailable: Boolean?
)

data class VidfastTrack(
    @param:JsonProperty("file") val file: String?,
    @param:JsonProperty("label") val label: String?
)

//Anilist
data class AnimeInfo(
    val title: String?,
    val romajiTitle: String?,
    val banner: String?,
    val description: String?
)
// --- Data Classes for AniList ---

data class AniListResponse(
    @param:JsonProperty("data") val data: AniListData?
)

data class AniListData(
    @param:JsonProperty("Media") val media: AniListMedia?
)

data class AniListMedia(
    @param:JsonProperty("title") val title: AniListTitle?,
    @param:JsonProperty("bannerImage") val bannerImage: String?,
    @param:JsonProperty("description") val description: String?
)

data class AniListTitle(
    @param:JsonProperty("english") val english: String?,
    @param:JsonProperty("romaji") val romaji: String?
)

//Vidlink
data class VidlinkResponse(
    @param:JsonProperty("stream") val stream: VidlinkStream
)

data class VidlinkStream(
    @param:JsonProperty("playlist") val playlist: String
)

data class TmdbDate(
    val today: String,
    val nextWeek: String,
    val lastWeekStart: String,
    val monthStart: String
)

//Tmdb
data class TmdbResponse(
    @param:JsonProperty("meta") val meta: TmdbMeta?
)

data class TmdbMeta(
    @param:JsonProperty("app_extras") val appExtras: TmdbAppExtras?
)

data class TmdbAppExtras(
    @param:JsonProperty("cast") val cast: List<TmdbCastMember>?
)

data class TmdbCastMember(
    @param:JsonProperty("name") val name: String?,
    @param:JsonProperty("character") val character: String?,
    @param:JsonProperty("photo") val photo: String?
)

//TMDB to mal
data class AniMedia(
    @param:JsonProperty("id") var id: Int? = null,
    @param:JsonProperty("idMal") var idMal: Int? = null
)

data class AniPage(@param:JsonProperty("media") var media: java.util.ArrayList<AniMedia> = arrayListOf())

data class AniData(@param:JsonProperty("Page") var Page: AniPage? = AniPage())

data class AniSearch(@param:JsonProperty("data") var data: AniData? = AniData())

data class AniIds(var id: Int? = null, var idMal: Int? = null)


//NF
data class NFVerifyUrl(
    val nfverifyurl: String
)

data class NfSearchData(
    val searchResult: List<NfSearchResult>,
)

data class NfSearchResult(
    val id: String,
    val t: String
)

data class NfPlaylist(
    val status: String,
    val usertoken: String,
    val video_link: String,
    val referer: String,
)

data class NetflixSources(
    @param:JsonProperty("file") val file: String? = null,
    @param:JsonProperty("label") val label: String? = null,
)

data class NetflixEpisodes(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("t") val t: String? = null,
    @param:JsonProperty("s") val s: String? = null,
    @param:JsonProperty("ep") val ep: String? = null,
)

data class NetflixSeason(
    @param:JsonProperty("s") val s: String? = null,
    @param:JsonProperty("id") val id: String? = null,
)

data class NetflixResponse(
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("year") val year : String? = null,
    @param:JsonProperty("season") val season: ArrayList<NetflixSeason>? = arrayListOf(),
    @param:JsonProperty("episodes") val episodes: ArrayList<NetflixEpisodes>? = arrayListOf(),
    @param:JsonProperty("sources") val sources: ArrayList<NetflixSources>? = arrayListOf(),
    @param:JsonProperty("nextPageShow") val nextPageShow: Int? = null,
)

//Malsync
data class MALSyncSites(
    @param:JsonProperty("animepahe") val animepahe: HashMap<String?, HashMap<String, String?>>? = hashMapOf(),
)

data class MALSyncResponses(
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("Sites") val sites: MALSyncSites? = null,
)

//Subtitles
data class WYZIESubtitle(
    val url: String,
    val language: String?,
    val display: String?,
)

data class Track(
    val lang: String,
    val code: String,
    val url: String,
    val type: String,
)

data class Url(
    val lang: String,
    val type: String,
    val link: String,
    val resulation: String,
)

data class CinemaOSReponse(
    val data: CinemaOSReponseData,
    val encrypted: Boolean,
)

data class CinemaOSReponseData(
    val encrypted: String,
    val cin: String,
    val mao: String,
    val salt: String,
)

data class CinemaOsAuthResponse(
    val token: String,
    val expiresIn: Long,
)

typealias TripleOneMoviesServerList = List<TripleOneMoviesServer>;

data class TripleOneMoviesServer(
    val name: String,
    val description: String,
    val image: String,
    val data: String,
)

data class TripleOneMoviesStream(
    val noReferrer: Boolean,
    val url: String,
)

//Reanime

data class ReanimeResponse(
    val success: Boolean,
    val servers: List<ReanimeServer>
)

data class ReanimeServer(
    val serverName: String,
    val dataLink: String,
    val dataType: String,
)

data class ResolvedReAnime(
    val result: ResolvedReAnimeResult,
)

data class ResolvedReAnimeResult(
    val token: String,
    val state: ResolvedReAnimeState,
)

data class ResolvedReAnimeState(
    val token: String,
)

data class ReAnimeStream(
    val result: ReAnimeStreamResult,
)

data class ReAnimeStreamResult(
    val stream: String,
)
