package com.eclipsia

import android.util.Log
import com.lagradost.cloudstream3.app
import org.json.JSONObject

// ── 1. Static APIs ──────────────────────────────────────────
const val malsyncAPI = "https://api.malsync.moe"
const val CC_COOKIE = BuildConfig.CC_COOKIE
const val videasyAPI = "https://api.videasy.net"
const val vidlinkAPI = "https://vidlink.pro"
const val multiDecryptAPI = "https://enc-dec.app/api"
const val notorrentAPI = "https://addon-osvh.onrender.com"
const val vidfastProApi = "https://vidfast.pro"
const val reanimeAPI = "https://reanime.to"

// ── 2. Dynamic API Config ────────────────────────────────────
// Loaded once via init() called from CineStream.load()
private var _apiConfig: JSONObject? = null

suspend fun init() {
    if (_apiConfig != null) return
    _apiConfig = runCatching {
        JSONObject(app.get("https://codeberg.org/eclipsia-404/eclipsia/raw/branch/main/urls.json").text)
    }.getOrElse {
        Log.e("CineStream", "Error loading dynamic API URLs: ${it.message}")
        JSONObject()
    }
}

private fun api(key: String) = _apiConfig?.optString(key).orEmpty()

// ── 3. Dynamic APIs ──────────────────────────────────────────
val nfmirrorAPI get() = api("nfmirror")
