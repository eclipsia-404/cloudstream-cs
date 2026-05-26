package com.eclipsia.settings

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import com.eclipsia.settings.SettingsTheme.dp


        // FRAME LAYOUT CONTAINER: Allows us to stack popup WebViews on top of the main one
        val webViewContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.WHITE)
            isFocusable = true; isFocusableInTouchMode = true

            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                useWideViewPort = true; loadWithOverviewMode = true
                setSupportZoom(false); builtInZoomControls = false
                userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"

                // Enable Multi-Window Support for Google Sign-in popups
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
            }

            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(TokenBridge(::deliver), "Android")

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d(TAG, "JS: ${consoleMessage?.message()}")
                    return super.onConsoleMessage(consoleMessage)
                }

                // Handles the popup creation when user clicks "Log in with Google"
                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                    Log.d(TAG, "Creating new popup window for OAuth flow.")
                    val popupWebView = WebView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = view.settings.userAgentString
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onCloseWindow(window: WebView) {
                                Log.d(TAG, "Popup window called close(). Destroying overlay.")
                                webViewContainer.removeView(window)
                                window.destroy()
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
                        }
                    }
                    webViewContainer.addView(popupWebView)
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    transport?.webView = popupWebView
                    resultMsg?.sendToTarget()
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                private fun updateUrl(url: String) { currentUrl = url; mainHandler.post { statusText.text = url } }

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) = updateUrl(url)

                override fun onPageFinished(view: WebView, url: String) {
                    updateUrl(url)
                    view.evaluateJavascript(SPOOF_JS, null)

                    if (type == LoginType.GRAMCINEMA) {
                        view.evaluateJavascript(GRAMCINEMA_INTERCEPT_JS, null)
                        var pollCount = 0
                        val poll = object : Runnable {
                            override fun run() {
                                if (tokenCaptured || pollCount++ >= 40) return
                                view.evaluateJavascript(cfg.extractJs) { res ->
                                    val clean = res?.trim()?.removeSurrounding("\"")?.trim()
                                    if (!clean.isNullOrBlank() && clean != "null") deliver(clean)
                                    else mainHandler.postDelayed(this, 1500)
                                }
                            }
                        }
                        mainHandler.postDelayed(poll, 1500)
                    }

                    val cookieToken = CookieManager.getInstance().getCookie(url)?.split(";")
                        ?.map { it.trim() }?.firstOrNull { it.startsWith("${cfg.tokenCookieName}=") }
                        ?.removePrefix("${cfg.tokenCookieName}=")

                    if (!cookieToken.isNullOrBlank()) { deliver(cookieToken); return }

                    if (!tokenCaptured && cfg.extractJs.isNotBlank()) {
                        view.evaluateJavascript(cfg.extractJs) { res ->
                            val clean = res?.trim()?.removeSurrounding("\"")?.trim()
                            if (!clean.isNullOrBlank() && clean != "null") deliver(clean)
                        }
                    }
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
            }
        }

        webViewContainer.addView(webView)

        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(context), 12.dp(context), 10.dp(context), 12.dp(context))
            background = theme.colorDrawable(theme.BG_CARD)

            addView(SettingsWidgets.accentBar(context, cfg.accentColor, cfg.accentColorB))
            addView(TextView(context).apply {
                text = cfg.title; textSize = 14f; setTypeface(null, Typeface.BOLD)
                setTextColor(theme.TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(SettingsWidgets.hSpacer(context, 8))
            addView(SettingsWidgets.pillBtn(context, "✕ Close", theme.TEXT_SECONDARY, theme.BG_CARD, Color.parseColor("#2A2D3E")) { dialogRef?.dismiss() })
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; background = theme.colorDrawable(theme.BG_DARK)
            addView(titleBar); addView(progressBar); addView(webViewContainer)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(16.dp(context), 6.dp(context), 16.dp(context), 8.dp(context))
                background = theme.colorDrawable(theme.BG_CARD)
                addView(statusText)
            })
        }

        dialogRef = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE); setContentView(root); setCancelable(true)
            setOnDismissListener { webView.stopLoading(); webView.destroy() }
            window?.apply {
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                setBackgroundDrawable(theme.colorDrawable(theme.BG_DARK))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    @Suppress("DEPRECATION") setDecorFitsSystemWindows(false)
                    root.setOnApplyWindowInsetsListener { view, insets ->
                        val ime = insets.getInsets(WindowInsets.Type.ime() or WindowInsets.Type.systemBars())
                        view.setPadding(ime.left, ime.top, ime.right, ime.bottom)
                        insets
                    }
                } else {
                    @Suppress("DEPRECATION") setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
            }
            show()
        }

        webView.loadUrl(cfg.startUrl)
        webView.requestFocus()
    }
}
