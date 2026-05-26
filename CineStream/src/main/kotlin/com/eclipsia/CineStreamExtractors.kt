package com.eclipsia

// Cloudstream Core & Utils
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.api.Log

import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

// Jackson
import com.fasterxml.jackson.annotation.JsonProperty

// Org JSON & Jsoup
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

// Java Security, IO, & Encoding
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

// Java Net
import java.net.URI
import java.net.URL
import java.net.URLEncoder

import com.eclipsia.settings.Settings

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object CineStreamExtractors {

    private val cfKiller by lazy { CloudflareKiller() }
    private val cfMutex = Mutex()

    suspend fun invokeAnimes(
        malId: Int? = null,
        aniId: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        origin: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mal_response = app.get("$malsyncAPI/mal/anime/${malId ?: return}").parsedSafe<MALSyncResponses>()

        Log.d("Malsync", "mal_response: $mal_response")

        val title = mal_response?.title
        val malsync = mal_response?.sites

        val animepaheUrl = malsync?.animepahe?.values?.firstNotNullOfOrNull {
            (it as? Map<*, *>)?.get("url") as? String
        }

        val animepaheTitle = malsync?.animepahe?.values?.firstNotNullOfOrNull {
            (it as? Map<*, *>)?.get("title") as? String
        }

        // Package the API results for the registry
        val malData = MalSyncData(title, animepaheUrl, aniId, malId, episode, year, origin, animepaheTitle)

        Log.d("Malsync", "malData: $malData")

        val executionList = Settings.activeProviderOrder.mapNotNull { key ->
            ProviderRegistry.builtInProviders.find { it.key == key }?.executeMalSync?.let { action ->
                suspend { this.action(malData, subtitleCallback, callback) }
            }
        }

        runLimitedAsync(concurrency = Settings.getConcurrency(), *executionList.toTypedArray())
    }

    private fun isCloudflarePage(response: NiceResponse): Boolean {
        val server = response.headers["Server"] ?: ""
        return server.contains("cloudflare", true) && response.code in listOf(403, 503)
    }

    suspend fun cfGet(url: String, headers: Map<String, String> = emptyMap(), allowRedirects: Boolean = true): NiceResponse {
        val response = app.get(url, headers = headers, allowRedirects = allowRedirects)
        return if (isCloudflarePage(response)) {
            cfMutex.withLock {
                val retryResponse = app.get(url, headers = headers, interceptor = cfKiller, allowRedirects = allowRedirects)
                if (isCloudflarePage(retryResponse)) {
                    cfKiller.savedCookies.clear()
                    app.get(url, headers = headers, interceptor = cfKiller, allowRedirects = allowRedirects)
                } else {
                    retryResponse
                }
            }
        } else {
            response
        }
    }

    suspend fun invokeVideasy(
        title: String? = null,
        tmdbId: Int? = null,
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val headers = mapOf(
            "Accept" to "*/*",
            "User-Agent" to USER_AGENT,
            "Origin" to "https://www.cineby.sc",
            "Referer" to "https://www.cineby.sc/"
        )

        val servers = listOf(
            "myflixerzupcloud",
            "1movies",
            "moviebox",
            "primewire",
            "m4uhd",
            "hdmovie",
            "cdn",
            "primesrcme",
            "visioncine",
            "overflix",
            "superflix",
            "cuevana",
            "lamovie",
            "mb-flix",
        )

        if(title == null) return

        val firstPass = quote(title)
        val encTitle = quote(firstPass)

        servers.safeAmap { server ->
            val url = if (season == null) {
                "$videasyAPI/$server/sources-with-title?title=$encTitle&mediaType=movie&year=$year&tmdbId=$tmdbId&imdbId=$imdbId"
            } else {
                "$videasyAPI/$server/sources-with-title?title=$encTitle&mediaType=tv&year=$year&tmdbId=$tmdbId&episodeId=$episode&seasonId=$season&imdbId=$imdbId"
            }

            val enc_data = app.get(url, headers = headers).text

            val jsonBody = mapOf("text" to enc_data, "id" to tmdbId)
            val response = app.post(
                "$multiDecryptAPI/dec-videasy",
                json = jsonBody
            )

            if(response.isSuccessful) {
                val json = response.text
                val result = JSONObject(json).getJSONObject("result")

                val sourcesArray = result.getJSONArray("sources")
                for (i in 0 until sourcesArray.length()) {
                    val obj = sourcesArray.getJSONObject(i)
                    val quality = obj.getString("quality")
                    val source = obj.getString("url")

                    val type = if(source.contains(".m3u8")) {
                        ExtractorLinkType.M3U8
                    } else if(source.contains(".mp4") || source.contains(".mkv")) {
                        ExtractorLinkType.VIDEO
                    } else {
                        INFER_TYPE
                    }

                    callback.invoke(
                        newExtractorLink(
                            "Videasy[${server.capitalizeServer()}]",
                            "Videasy[${server.capitalizeServer()}] $quality",
                            source,
                            type
                        ) {
                            this.quality = getIndexQuality(quality)
                            this.headers = headers
                        }
                    )
                }

                val subtitlesArray = result.getJSONArray("subtitles")
                for (i in 0 until subtitlesArray.length()) {
                    val obj = subtitlesArray.getJSONObject(i)
                    val source = obj.getString("url")
                    val language = obj.getString("language")

                    subtitleCallback.invoke(
                        newSubtitleFile(
                            getLanguage(language) ?: language,
                            source
                        )
                    )
                }
            }
        }
    }

    suspend fun invokeVidlink(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = "$multiDecryptAPI/enc-vidlink?text=$tmdbId"
        val json = app.get(url).text

        Log.d("Vidlink", "enc response: $json")

        val enc_data = JSONObject(json).getString("result")

        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Connection" to "keep-alive",
            "Referer" to "$vidlinkAPI/",
            "Origin" to vidlinkAPI,
        )

        val epUrl = if(season == null) {
            "$vidlinkAPI/api/b/movie/$enc_data"
        } else {
            "$vidlinkAPI/api/b/tv/$enc_data/$season/$episode"
        }

        val epJson = app.get(epUrl, headers = headers).text

        Log.d("Vidlink", "ep response: $epJson")

        val data = parseJson<VidlinkResponse>(epJson)
        val m3u8 = data.stream.playlist

        M3u8Helper.generateM3u8(
            "Vidlink",
            m3u8,
            "$vidlinkAPI/",
            headers = headers
        ).forEach(callback)
    }

    suspend fun invokeNetmirror(
        serviceName: String,
        ottCode: String,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf(
            "ott" to ottCode,
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:151.0) Gecko/20100101 Firefox/151.0 /OS.GatuNewTV v1.0",
            "x-requested-with" to "NetmirrorNewTV v1.0"
        )

        val searchUrl = "$nfmirrorAPI/search.php?s=$title"
        val searchData = app.get(searchUrl, headers = headers).parsedSafe<NfSearchData>()

        Log.d("Netmirror", "$serviceName searchData: $searchData")

        val netId = searchData?.searchResult?.firstOrNull { it.t.equals("${title?.trim()}", true) }?.id ?: return

        Log.d("Netmirror", "$serviceName netId: $netId")

        val finalId = app.get("$nfmirrorAPI/post.php?id=$netId", headers = headers)
            .parsedSafe<NetflixResponse>().let { media ->
                if (season == null) {
                    netId
                } else {
                    val seasonId = media?.season?.find { it.s.toString().contains("Season $season") }?.id
                    var episodeId: String? = null
                    var page = 1

                    // Loop for episodes
                    while (episodeId == null && page < 10) {
                        val epUrl = "$nfmirrorAPI/episodes.php?id=$seasonId&page=$page"
                        val data = app.get(epUrl, headers = headers).parsedSafe<NetflixResponse>()

                        Log.d("Netmirror", "$serviceName data: $data")

                        episodeId = data?.episodes?.find { it.ep == "$episode" }?.id
                        if ((data?.nextPageShow ?: 0) != 1) break
                        page++
                    }
                    episodeId
                }
        }

        if (finalId == null) return

        Log.d("Netmirror", "$serviceName finalId: $finalId")

        val playlistUrl = "$nfmirrorAPI/player.php?id=$finalId"

        val playlist = app.get(
            playlistUrl,
            headers = headers,
        ).parsed<NfPlaylist>()

        Log.d("Netmirror", "$serviceName playlist: $playlist")

        callback.invoke(
            newExtractorLink(
                serviceName,
                serviceName,
                playlist.video_link,
                ExtractorLinkType.M3U8
            ) {
                this.referer = playlist.referer
                this.quality = Qualities.P1080.value
            }
        )
    }
    
    suspend fun invokeWYZIESubs(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val url = if(season != null) "$WYZIESubsAPI/search?id=$id&season=$season&episode=$episode&source=all&key=${Settings.getWyzieSubsKey() ?: return}" else "$WYZIESubsAPI/search?id=$id&source=all&key=${Settings.getWyzieSubsKey() ?: return}"
        val json = app.get(url, timeout = 10000).text
        Log.d("WyzieSubs", "Received subtitle response: $json")
        val data = parseJson<ArrayList<WYZIESubtitle>>(json)

        data.forEach {
            val lang = it.display ?: it.language
            subtitleCallback.invoke(
                newSubtitleFile(
                    getLanguage(lang) ?: return@forEach,
                    it.url
                )
            )
        }
    }
    
    suspend fun invokeVidFastPro(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) "$vidfastProApi/movie/$tmdbId/" else "$vidfastProApi/tv/$tmdbId/$season/$episode/"

        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$vidfastProApi/",
            "X-Requested-With" to "XMLHttpRequest",
        )

        val response = app.get(url, headers = headers).text
        val encodedText = Regex("""\\"en\\":\\"(.*?)\\""").find(response)?.groupValues?.get(1) ?: return
        val decApiUrl = "$multiDecryptAPI/enc-vidfast?text=$encodedText&version=1"
        val decodedDataJson = app.get(decApiUrl).text
        val decodedData = tryParseJson<EncDecResponse>(decodedDataJson)?.result ?: return
        val serversUrl = decodedData.servers ?: return
        val streamBaseUrl = decodedData.stream ?: return
        val token = decodedData.token ?: return
        headers["X-CSRF-Token"] = token

        val serversEncrypted = app.post(serversUrl, headers = headers).text
        val serversListJson = app.post(
            "$multiDecryptAPI/dec-vidfast",
            json = mapOf("text" to serversEncrypted, "version" to "1")
        ).text

        val serversList = tryParseJson<VidfastStreamResponse>(serversListJson)?.result ?: return

        serversList.safeAmap { server ->
            val serverHash = server.data ?: return@safeAmap
            val finalStreamUrl = "$streamBaseUrl/$serverHash"

            val streamDataEncrypted = app.post(finalStreamUrl, headers = headers).text

            if(streamDataEncrypted.isNullOrBlank()) return@safeAmap

            val streamDataJson = app.post(
                "$multiDecryptAPI/dec-vidfast",
                json = mapOf("text" to streamDataEncrypted , "version" to "1")
            ).text

            val streamData = tryParseJson<VidfastServersStreamRoot>(streamDataJson)?.result ?: return@safeAmap

            streamData.tracks?.forEach { track ->
                if (track.file != null && track.label != null) {
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            getLanguage(track.label) ?: track.label,
                            track.file
                        )
                    )
                }
            }

            val fileUrl = streamData.url ?: return@safeAmap
            val type = if (fileUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

            val is4k = streamData.is4kAvailable == true || server.description?.contains("4K", true) == true
            val quality = if (is4k) Qualities.P2160.value else Qualities.P1080.value

            callback.invoke(
                newExtractorLink(
                    "Vidfast[${server.name}]",
                    "Vidfast[${server.name}] ${server.description ?: ""}",
                    fileUrl,
                    type
                ) {
                    this.headers = headers
                    this.quality = quality
                }
            )
        }
    }

    suspend fun invokeReanime(
        aniId: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf(
            "Referer" to "$reanimeAPI/",
            "User-Agent" to USER_AGENT
        )

        val response = app.get(
            "$reanimeAPI/api/flix/$aniId/${episode ?: 1}",
            headers = headers
        ).parsedSafe<ReanimeResponse>() ?: return

        if (!response.success) return

        response.servers.safeAmap { server ->
            val type = server.dataType.capitalizeServer()
            val dataLink = server.dataLink
            loadCustomExtractor("Reanime[$type]", dataLink, "", subtitleCallback, callback)
        }
    }
