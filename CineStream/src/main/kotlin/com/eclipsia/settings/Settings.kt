package com.eclipsia.settings

import android.content.Context
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.eclipsia.ProviderRegistry

object Settings {

    // ── Global keys ──────────────────────────────────────────
    const val CONCURRENCY_KEY          = "ScrapeConcurrency"
    const val DOWNLOAD_ENABLE          = "DownloadEnable"
    const val PROVIDER_CINESTREAM      = "ProviderCineStream"
    const val PROVIDER_TMDB            = "ProviderTmdb"
    const val WYZIE_SUBS_KEY           = "wyzie_subs_api_key"
    const val NEW_PROVIDER_DEFAULT_ON  = "new_provider_default_on"

    private const val SEEN_PROVIDERS_KEY = "seen_providers"
    private const val PROVIDER_ORDER_KEY = "provider_order"

    // ── Configuration Getters ────────────────────────────────
    fun getConcurrency(): Int = getKey<Int>(CONCURRENCY_KEY) ?: 8

    val allowDownloadLinks: Boolean
        get() = getKey<Boolean>(DOWNLOAD_ENABLE) ?: false

    val activeProviderOrder: List<String>
        get() = getOrder().filter { enabled(it) }

    // ── Dynamic Provider Maps ────────────────────────────────
    // We dynamically pull these from our single source of truth (ProviderRegistry)!
    val TORRENT_KEYS: Set<String> get() = ProviderRegistry.torrentKeys
    val PROVIDER_NAMES: Map<String, String> get() = ProviderRegistry.namesMap
    val DEFAULT_ORDER: List<String> get() = ProviderRegistry.keys

    // ── Provider ordering ────────────────────────────────────

    fun newProviderDefaultOn(): Boolean = getKey<Boolean>(NEW_PROVIDER_DEFAULT_ON) ?: true

    fun getSeenProviders(): Set<String> =
        getKey<String>(SEEN_PROVIDERS_KEY)
            ?.split(",")?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()

    fun initSeenProviders() {
        val allKnown    = (DEFAULT_ORDER + stremioKeys).toSet()
        val currentSeen = getSeenProviders()

        if (currentSeen.isEmpty()) {
            setKey(SEEN_PROVIDERS_KEY, allKnown.joinToString(","))
            return
        }

    // ── Wyzie Subs API key helpers ───────────────────────────
    fun saveWyzieSubsKey(key: String) = setKey(WYZIE_SUBS_KEY, key.trim())
    fun getWyzieSubsKey(): String?     = getKey<String>(WYZIE_SUBS_KEY)?.takeIf { it.isNotBlank() }
    fun clearWyzieSubsKey()            = setKey(WYZIE_SUBS_KEY, null)

    // ── Entry point ──────────────────────────────────────────
    fun showSettingsDialog(context: Context, onSave: () -> Unit) =
        SettingsDialog.show(context, onSave)
}
