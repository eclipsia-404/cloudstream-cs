package com.eclipsia.settings

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.eclipsia.settings.SettingsTheme.dp

/**
 * Assembles the full Settings AlertDialog and every collapsible card inside it.
 * Data access goes through Settings; theme tokens come from SettingsTheme;
 * reusable widgets come from SettingsWidgets; the provider card comes from SettingsProviders.
 */
internal object SettingsDialog {

    fun show(context: Context, onSave: () -> Unit) {
        val theme          = SettingsTheme
        var requireRestart = false
        val pending        = mutableMapOf<String, Any?>()
        var commitOrder:  () -> Unit = {}
        var commitAddons: () -> Unit = {}

        val scroll = ScrollView(context).apply {
            isScrollbarFadingEnabled = true
            background               = theme.colorDrawable(theme.BG_DARK)
            isFocusable              = false
            descendantFocusability   = ScrollView.FOCUS_AFTER_DESCENDANTS
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24.dp(context))
            background = theme.colorDrawable(theme.BG_DARK)
        }

        layout.addView(buildHeroBanner(context))
        layout.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8.dp(context))
        })

        // Scraping settings card
        layout.addView(buildCollapsibleCard(context, "⚙️  Scraping Settings",
            accentA = Color.parseColor("#06B6D4"), accentB = Color.parseColor("#0891B2")) {
            addView(buildToggleRow(context, "Download Only Links",
                "Only great for downloading (Not for Streaming)",
                Settings.DOWNLOAD_ENABLE, false, pending))
            addView(SettingsWidgets.divider(context))
            addView(buildConcurrencyRow(context, pending))
        })

        // API Tokens card (Febbox + Wyzie Subs)
        layout.addView(buildApiTokensCard(context, pending))

        // Active catalogs card
        val onCatalogChanged: () -> Unit = {
            requireRestart = true
        }
        layout.addView(buildCollapsibleCard(context, "📡  Active Catalogs",
            accentA = Color.parseColor("#10B981"), accentB = Color.parseColor("#059669")) {
            addView(buildToggleRow(context, "CineStream", "Cinemeta catalog",
                Settings.PROVIDER_CINESTREAM, true, pending, onCatalogChanged))
            addView(SettingsWidgets.divider(context))

            addView(buildToggleRow(context, "Eclipsia", "TMDB catalog",
                Settings.PROVIDER_TMDB, true, pending, onCatalogChanged))
        })

        // Providers card (delegated)
        layout.addView(SettingsProviders.buildCard(context, pending) { commit -> commitOrder = commit })

        // Stremio addons card
        layout.addView(buildStremioAddonsCard(context) { commit -> commitAddons = commit })

        // Credits card
        layout.addView(buildCreditsCard(context))

        scroll.addView(layout)

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog)
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                pending.forEach { (key, value) ->
                    when {
                        key == Settings.SHOWBOX_TOKEN_KEY && value == null   -> Settings.clearShowboxToken()
                        key == Settings.SHOWBOX_TOKEN_KEY && value is String -> Settings.saveShowboxToken(value)
                        key == Settings.WYZIE_SUBS_KEY    && value == null   -> Settings.clearWyzieSubsKey()
                        key == Settings.WYZIE_SUBS_KEY    && value is String -> Settings.saveWyzieSubsKey(value)
                        key == Settings.GRAMCINEMA_TOKEN_KEY && value == null   -> Settings.clearGramCinemaToken()
                        key == Settings.GRAMCINEMA_TOKEN_KEY && value is String -> Settings.saveGramCinemaToken(value)
                        value is Boolean                                     -> com.lagradost.cloudstream3.CloudStreamApp.setKey(key, value)
                        value is Int                                         -> com.lagradost.cloudstream3.CloudStreamApp.setKey(key, value)
                        value == null                                        -> com.lagradost.cloudstream3.CloudStreamApp.setKey(key, null as String?)
                    }
                }
                commitAddons(); commitOrder()
                if (requireRestart) showRestartWarning(context, onSave) else onSave()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(theme.roundRect(theme.BG_DARK, 20f.dp(context)))
        dialog.show()
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.apply { setTextColor(theme.ACCENT_START); isAllCaps = false }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            ?.apply { setTextColor(theme.TEXT_SECONDARY); isAllCaps = false }
    }

    // =========================================================
    //  COLLAPSIBLE CARD TEMPLATE
    // =========================================================

    fun buildCollapsibleCard(
        context: Context,
        title: String,
        startExpanded: Boolean = false,
        accentA: Int = SettingsTheme.ACCENT_START,
        accentB: Int = SettingsTheme.ACCENT_END,
        block: LinearLayout.() -> Unit
    ): View {
        val theme = SettingsTheme
        val card  = SettingsWidgets.cardContainer(context)

        var expanded = startExpanded
        val content  = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 8.dp(context))
            visibility  = if (expanded) View.VISIBLE else View.GONE
        }
        content.block()

        val chevron = TextView(context).apply {
            text = if (expanded) "▲" else "▼"; textSize = 11f; setTextColor(theme.TEXT_SECONDARY)
        }

        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20.dp(context), 16.dp(context), 16.dp(context), 16.dp(context))
            gravity     = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true; isFocusableInTouchMode = false
            background  = theme.stateDrawable(context)

            addView(SettingsWidgets.accentBar(context, accentA, accentB))
            addView(TextView(context).apply {
                text = title; textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(theme.TEXT_SECONDARY); letterSpacing = 0.08f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(chevron)

            setOnClickListener {
                expanded = !expanded; chevron.text = if (expanded) "▲" else "▼"
                SettingsWidgets.animateExpand(content, expanded)
            }
        })

        card.addView(content)
        SettingsWidgets.fadeInSlide(card)
        return card
    }

    // =========================================================
    //  TOGGLE ROW
    // =========================================================

    fun buildToggleRow(
        context: Context, label: String, subtitle: String,
        databaseKey: String, defaultState: Boolean,
        pending: MutableMap<String, Any?>,
        onChanged: () -> Unit = {}
    ): View {
        val theme   = SettingsTheme
        val checked = pending[databaseKey] as? Boolean
            ?: com.lagradost.cloudstream3.CloudStreamApp.getKey<Boolean>(databaseKey) ?: defaultState

        val sw = SettingsWidgets.styledSwitch(context, checked)

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20.dp(context), 14.dp(context), 16.dp(context), 14.dp(context))
            gravity     = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true; isFocusableInTouchMode = false
            background  = theme.stateDrawable(context)

            val textCol = LinearLayout(context).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(context).apply {
                text = label; textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(theme.TEXT_PRIMARY)
            })
            textCol.addView(TextView(context).apply {
                text = subtitle; textSize = 12f; setTextColor(theme.TEXT_SECONDARY)
                setPadding(0, 3.dp(context), 0, 0)
            })
            addView(textCol); addView(sw)

            setOnClickListener {
                sw.isChecked = !sw.isChecked
                pending[databaseKey] = sw.isChecked
                sw.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
                    sw.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }.start()
                onChanged()
            }
        }
    }

    // =========================================================
    //  API TOKENS CARD  (Febbox + Wyzie Subs)
    // =========================================================

    private fun buildApiTokensCard(
        context: Context,
        pending: MutableMap<String, Any?>
    ): View {
        val theme          = SettingsTheme
        val TOKEN_ACCENT   = Color.parseColor("#F59E0B")
        val CLIP_TEXT      = Color.parseColor("#94A3B8")
        val CLIP_BG        = Color.parseColor("#0F1520")
        val CLIP_BORDER    = Color.parseColor("#1E2A3A")
        val WYZIE_ACCENT   = Color.parseColor("#818CF8")
        val WYZIE_BG       = Color.parseColor("#0E0C1A")
        val WYZIE_BORDER   = Color.parseColor("#2E2060")

        val card = SettingsWidgets.cardContainer(context)

        var expanded = false
        val content  = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(context), 4.dp(context), 16.dp(context), 16.dp(context))
            visibility  = View.GONE
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // ── helper: build a self-contained token input section ──
        fun buildTokenSection(
            labelText: String,
            hint: String,
            pendingKey: String,
            getCurrent: () -> String?,
            sectionAccent: Int,
            sectionBg: Int,
            sectionBorder: Int,
            clipLabel: String,
            loginType: SettingsWebView.LoginType? = null  // when set, shows a 🌐 Login button
        ): View {
            val sectionLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            sectionLayout.addView(TextView(context).apply {
                text = labelText; textSize = 12f; setTextColor(theme.TEXT_SECONDARY)
                setPadding(4.dp(context), 0, 4.dp(context), 10.dp(context))
            })

            val initialVal = (pending[pendingKey] as? String) ?: getCurrent() ?: ""
            val input = EditText(context).apply {
                this.hint = hint; setText(initialVal)
                setTextColor(theme.TEXT_PRIMARY); setHintTextColor(theme.TEXT_SECONDARY)
                textSize = 13f; setSingleLine(true)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                isFocusable = true; isFocusableInTouchMode = true
                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) (v.parent?.parent?.parent?.parent as? ScrollView)?.requestChildFocus(v, v)
                }
                setPadding(14.dp(context), 12.dp(context), 14.dp(context), 12.dp(context))
                background = theme.inputBackground(context)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 8.dp(context) }
            }
            sectionLayout.addView(input)

            // Hoist savedBadge so the Login callback can update it
            val savedBadge = TextView(context).apply {
                text = when {
                    pending.containsKey(pendingKey) ->
                        if ((pending[pendingKey] as? String) != null) "✓ Staged" else ""
                    getCurrent() != null -> "✓ Saved"
                    else -> ""
                }
                textSize = 10f; setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#4ADE80"))
                setPadding(0, 0, 8.dp(context), 0)
            }

            // Paste / Copy / Login row
            sectionLayout.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 10.dp(context) }

                addView(SettingsWidgets.pillBtn(context, "📋 Paste", CLIP_TEXT, CLIP_BG, CLIP_BORDER) {
                    val clip = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                    if (!clip.isNullOrBlank()) {
                        input.setText(clip); input.setSelection(input.text?.length ?: 0)
                        Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                })
                addView(SettingsWidgets.hSpacer(context, 8))
                addView(SettingsWidgets.pillBtn(context, "📄 Copy", CLIP_TEXT, CLIP_BG, CLIP_BORDER) {
                    val text = input.text?.toString()?.trim()
                    if (!text.isNullOrBlank()) {
                        clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, "Nothing to copy", Toast.LENGTH_SHORT).show()
                })
                // Optional WebView login button
                if (loginType != null) {
                    addView(SettingsWidgets.hSpacer(context, 8))
                    addView(SettingsWidgets.pillBtn(
                        context, "🌐 Login",
                        sectionAccent, sectionBg, sectionBorder
                    ) {
                        SettingsWebView.show(context, loginType) { token ->
                            // Auto-fill field and stage the token
                            input.setText(token)
                            input.setSelection(token.length)
                            pending[pendingKey] = token
                            savedBadge.text = "✓ Staged"
                        }
                    })
                }
            })

            // Show/Hide | Clear | Save row
            sectionLayout.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                var isVisible = false

                addView(TextView(context).apply {
                    text = "Show"; textSize = 11f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(theme.TEXT_SECONDARY)
                    setPadding(0, 0, 12.dp(context), 0)
                    isClickable = true; isFocusable = true; isFocusableInTouchMode = false
                    setOnClickListener {
                        isVisible = !isVisible
                        input.inputType = if (isVisible)
                            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        else
                            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        input.setSelection(input.text?.length ?: 0)
                        text = if (isVisible) "Hide" else "Show"
                    }
                })
                addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
                addView(savedBadge)
                addView(SettingsWidgets.dangerBtn(context, "Clear") {
                    input.setText(""); pending[pendingKey] = null; savedBadge.text = ""
                    Toast.makeText(context, "Cleared — tap outer Save to apply", Toast.LENGTH_SHORT).show()
                })
                addView(SettingsWidgets.hSpacer(context, 8))
                addView(SettingsWidgets.pillBtn(context, "Save", sectionAccent, sectionBg, sectionBorder) {
                    val token = input.text.toString().trim()
                    if (token.isBlank()) Toast.makeText(context, "Cannot be empty", Toast.LENGTH_SHORT).show()
                    else {
                        pending[pendingKey] = token; savedBadge.text = "✓ Staged"
                        Toast.makeText(context, "✓ Staged — tap outer Save to apply", Toast.LENGTH_SHORT).show()
                    }
                })
            })

            return sectionLayout
        }

        // ── Divider between sections ─────────────────────────────
        content.addView(SettingsWidgets.divider(context).apply {
            (layoutParams as? LinearLayout.LayoutParams)
                ?.setMargins(0, 12.dp(context), 0, 12.dp(context))
        })

        // ── Wyzie Subs section header ────────────────────────────
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 6.dp(context) }

            addView(TextView(context).apply {
                text = "📝  Wyzie Subs API Key"
                textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(theme.TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(SettingsWidgets.pillBtn(context, "🔑 Get Key", WYZIE_ACCENT, WYZIE_BG, WYZIE_BORDER) {
                try {
                    context.startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://sub.wyzie.io/redeem")
                    ))
                } catch (_: Exception) {
                    Toast.makeText(context, "https://sub.wyzie.io/redeem", Toast.LENGTH_LONG).show()
                }
            })
        })

        // ── Wyzie Subs token input ───────────────────────────────
        content.addView(buildTokenSection(
            labelText     = "Enter your Wyzie Subs API key to unlock subtitle sources",
            hint          = "Paste API key",
            pendingKey    = Settings.WYZIE_SUBS_KEY,
            getCurrent    = Settings::getWyzieSubsKey,
            sectionAccent = WYZIE_ACCENT,
            sectionBg     = WYZIE_BG,
            sectionBorder = WYZIE_BORDER,
            clipLabel     = "Wyzie Subs Key"
        ))

        // ── Card header (badge reflects whichever token was last staged) ──
        fun anyBadgeText() = when {
            (pending[Settings.SHOWBOX_TOKEN_KEY]     as? String) != null ||
            (pending[Settings.WYZIE_SUBS_KEY]        as? String) != null ||
            (pending[Settings.GRAMCINEMA_TOKEN_KEY]  as? String) != null -> "✓ Staged"
            Settings.getShowboxToken()    != null ||
            Settings.getWyzieSubsKey()    != null ||
            Settings.getGramCinemaToken() != null -> "✓ Saved"
            else -> ""
        }

        val savedBadge = TextView(context).apply {
            text = anyBadgeText()
            textSize = 10f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#4ADE80"))
            setPadding(0, 0, 8.dp(context), 0)
        }

        val chevron = TextView(context).apply { text = "▼"; textSize = 11f; setTextColor(theme.TEXT_SECONDARY) }

        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20.dp(context), 16.dp(context), 16.dp(context), 16.dp(context))
            gravity     = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true; isFocusableInTouchMode = false
            background  = theme.stateDrawable(context)

            addView(SettingsWidgets.accentBar(context, TOKEN_ACCENT, Color.parseColor("#818CF8")))
            addView(TextView(context).apply {
                text = "🔑  API Tokens"; textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(theme.TEXT_SECONDARY); letterSpacing = 0.08f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(savedBadge); addView(chevron)

            setOnClickListener {
                expanded = !expanded; chevron.text = if (expanded) "▲" else "▼"
                if (!expanded) savedBadge.text = anyBadgeText()
                SettingsWidgets.animateExpand(content, expanded)
            }
        })

        card.addView(content)
        SettingsWidgets.fadeInSlide(card)
        return card
    }

    // =========================================================
    //  CONCURRENCY ROW
    // =========================================================

    private fun buildConcurrencyRow(
        context: Context,
        pending: MutableMap<String, Any?>
    ): View {
        val theme = SettingsTheme
        var currentVal = pending[Settings.CONCURRENCY_KEY] as? Int ?: Settings.getConcurrency()

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20.dp(context), 14.dp(context), 16.dp(context), 14.dp(context))
            gravity = Gravity.CENTER_VERTICAL
            background = theme.stateDrawable(context)

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(context).apply {
                text = "Scraping Concurrency"
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(theme.TEXT_PRIMARY)
            })
            textCol.addView(TextView(context).apply {
                text = "Number of providers run concurrently\n⚠️ For low end devices set it low"
                textSize = 12f
                setTextColor(theme.TEXT_SECONDARY)
                setPadding(0, 3.dp(context), 0, 0)
            })
            addView(textCol)

            val valText = TextView(context).apply {
                text = currentVal.toString()
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(theme.ACCENT_START)
                gravity = Gravity.CENTER
                minWidth = 32.dp(context)
            }

            addView(SettingsWidgets.pillBtn(context, " - ", theme.TEXT_PRIMARY, Color.parseColor("#1A1E28"), Color.parseColor("#2E2850")) {
                if (currentVal > 1) {
                    currentVal--
                    pending[Settings.CONCURRENCY_KEY] = currentVal
                    valText.text = currentVal.toString()
                }
            })
            addView(valText)
            addView(SettingsWidgets.pillBtn(context, " + ", theme.TEXT_PRIMARY, Color.parseColor("#1A1E28"), Color.parseColor("#2E2850")) {
                if (currentVal < 50) {
                    currentVal++
                    pending[Settings.CONCURRENCY_KEY] = currentVal
                    valText.text = currentVal.toString()
                }
            })
        }
    }

    // =========================================================
    //  HERO BANNER
    // =========================================================

    private fun buildHeroBanner(context: Context): View {
        val theme = SettingsTheme
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28.dp(context), 32.dp(context), 28.dp(context), 24.dp(context))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#1A1730"), theme.BG_DARK))

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(48.dp(context), 4.dp(context))
                    .also { it.bottomMargin = 16.dp(context) }
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(theme.ACCENT_START, theme.ACCENT_END)).apply { cornerRadius = 99f }
            })
            addView(TextView(context).apply {
                text = "Plugin Settings"; textSize = 22f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(theme.TEXT_PRIMARY); letterSpacing = -0.02f
            })
            addView(TextView(context).apply {
                text = "Configure sources, catalogs & cookies"
                textSize = 13f; setTextColor(theme.TEXT_SECONDARY); setPadding(0, 6.dp(context), 0, 0)
            })
        }
    }

    private fun showRestartWarning(context: Context, onSave: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Restart Required ⚠️")
            .setMessage("You've changed your Active Catalogs.\n\nPlease fully close and reopen Cloudstream for providers to update.")
            .setPositiveButton("Got it") { _, _ -> onSave() }
            .setCancelable(false).show()
    }

    // =========================================================
    //  MICRO HELPERS
    // =========================================================

    private fun labelText(context: Context, text: String) = TextView(context).apply {
        this.text = text; textSize = 11f; setTextColor(SettingsTheme.TEXT_SECONDARY)
        setPadding(0, 0, 0, 4.dp(context))
    }

    private fun simpleWatcher(onChange: (String) -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { onChange(s?.toString() ?: "") }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }
}
