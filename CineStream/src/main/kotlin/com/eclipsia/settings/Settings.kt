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
    const val PROVIDER_SIMKL           = "ProviderSimkl"
    const val PROVIDER_TMDB            = "ProviderTmdb"
    const val NEW_PROVIDER_DEFAULT_ON  = "new_provider_default_on"

    private const val SEEN_PROVIDERS_KEY = "seen_providers"
    private const val PROVIDER_ORDER_KEY = "provider_order"

    // ── Configuration Getters ────────────────────────────────
    fun getConcurrency(): Int = getKey<Int>(CONCURRENCY_KEY) ?: 10

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
        val stremioKeys = getStremioAddons().map { stremioAddonKey(it.name) }
        val allKnown    = (DEFAULT_ORDER + stremioKeys).toSet()
        val currentSeen = getSeenProviders()

        if (currentSeen.isEmpty()) {
            setKey(SEEN_PROVIDERS_KEY, allKnown.joinToString(","))
            return
        }

        val defaultState = newProviderDefaultOn()

        allKnown.forEach { key ->
            if (key !in currentSeen
                && !key.startsWith("stremio_")
                && key !in TORRENT_KEYS
                && getKey<Boolean>(key) == null
            ) {
                setKey(key, defaultState)
            }
        }

        val removedKeys = currentSeen - allKnown
        removedKeys.forEach { setKey(it, null as Boolean?) }
        val prunedSeen  = (currentSeen - removedKeys) + allKnown
        setKey(SEEN_PROVIDERS_KEY, prunedSeen.joinToString(","))
    }

    fun markProvidersSeen(keys: Collection<String>) {
        val currentlySeen = getSeenProviders()
        val defaultState  = newProviderDefaultOn()
        keys.forEach { key ->
            if (key !in currentlySeen
                && !key.startsWith("stremio_")
                && key !in TORRENT_KEYS
                && getKey<Boolean>(key) == null
            ) {
                setKey(key, defaultState)
            }
        }
        val merged = currentlySeen + keys
        setKey(SEEN_PROVIDERS_KEY, merged.joinToString(","))
    }

    fun enabled(key: String): Boolean {
        val explicit = getKey<Boolean>(key)
        if (explicit != null) return explicit
        if (key.startsWith("stremio_")) return true
        if (key in TORRENT_KEYS) return false
        if (key !in getSeenProviders()) return newProviderDefaultOn()
        return true
    }

    fun getOrder(): List<String> {
        val stremioKeys = getStremioAddons().map { stremioAddonKey(it.name) }
        val allKnown    = DEFAULT_ORDER + stremioKeys
        val saved       = getKey<String>(PROVIDER_ORDER_KEY)
            ?.split(",")?.filter { it.isNotBlank() }
            ?: return allKnown

        val validSaved = saved.filter { it in allKnown }
        return validSaved + (allKnown - validSaved.toSet())
    }

    fun saveOrder(order: List<String>) =
        setKey(PROVIDER_ORDER_KEY, order.joinToString(","))

    // ── Entry point ──────────────────────────────────────────
    fun showSettingsDialog(context: Context, onSave: () -> Unit) =
        SettingsDialog.show(context, onSave)
}
