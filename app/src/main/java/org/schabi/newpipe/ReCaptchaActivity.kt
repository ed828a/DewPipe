package org.schabi.newpipe

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.support.v4.app.NavUtils
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/*
 * Created by beneth <bmauduit@beneth.fr> on 06.12.16.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * ReCaptchaActivity.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
class ReCaptchaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recaptcha)

        // Set return to Cancel by default
        setResult(Activity.RESULT_CANCELED)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setTitle(R.string.reCaptcha_title)
            actionBar.setDisplayShowTitleEnabled(true)
        }

        val myWebView = findViewById<WebView>(R.id.reCaptchaWebView)

        // Enable Javascript
        val webSettings = myWebView.settings
        webSettings.javaScriptEnabled = true

        val webClient = ReCaptchaWebViewClient(this)
        myWebView.webViewClient = webClient

        // Cleaning cache, history and cookies from webView
        myWebView.clearCache(true)
        myWebView.clearHistory()
        val cookieManager = CookieManager.getInstance()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies { }
        } else {
            cookieManager.removeAllCookie()
        }

        myWebView.loadUrl(YT_URL)
    }

    private inner class ReCaptchaWebViewClient internal constructor(private val context: Activity) : WebViewClient() {
        private var mCookies: String? = null


        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap) {
            // TODO: Start Loader
            super.onPageStarted(view, url, favicon)
            Log.d(TAG, "ReCaptchaWebViewClient() called")
        }

        override fun onPageFinished(view: WebView, url: String) {
            val cookies = CookieManager.getInstance().getCookie(url)

            // TODO: Stop Loader

            // find cookies : s_gl & goojf and Add cookies to Downloader
            if (find_access_cookies(cookies)) {
                // Give cookies to Downloader class
                Downloader.getInstance().cookies = mCookies

                // Closing activity and return to parent
                setResult(Activity.RESULT_OK)
                finish()
            }
        }

        private fun find_access_cookies(cookies: String): Boolean {
            var ret = false
            var c_s_gl = ""
            var c_goojf = ""

            val parts = cookies.split("; ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (part in parts) {
                if (part.trim { it <= ' ' }.startsWith("s_gl")) {
                    c_s_gl = part.trim { it <= ' ' }
                }
                if (part.trim { it <= ' ' }.startsWith("goojf")) {
                    c_goojf = part.trim { it <= ' ' }
                }
            }
            if (c_s_gl.length > 0 && c_goojf.length > 0) {
                ret = true
                //mCookies = c_s_gl + "; " + c_goojf;
                // Youtube seems to also need the other cookies:
                mCookies = cookies
            }

            return ret
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            android.R.id.home -> {
                val intent = Intent(this, org.schabi.newpipe.MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                NavUtils.navigateUpTo(this, intent)
                return true
            }
            else -> return false
        }
    }

    companion object {
        const val RECAPTCHA_REQUEST = 10

        val TAG = ReCaptchaActivity::class.java.toString()
        const val YT_URL = "https://www.youtube.com"
    }
}
