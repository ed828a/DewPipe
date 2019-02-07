package org.schabi.newpipe

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.support.v4.app.NavUtils
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * this is just verify if Youtube web is still functioning
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
        actionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setTitle(R.string.reCaptcha_title)
            it.setDisplayShowTitleEnabled(true)
        }

        val myWebView = findViewById<WebView>(R.id.reCaptchaWebView)

        // Enable Javascript
        val webSettings = myWebView.settings
        webSettings.javaScriptEnabled = true

        val webClient = ReCaptchaWebViewClient(this)
        myWebView.webViewClient = webClient

        // Cleaning cache, history and cookies getTabFrom webView
        myWebView.clearCache(true)
        myWebView.clearHistory()
        val cookieManager = CookieManager.getInstance()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(null)
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
            if (findAccessCookies(cookies)) {
                // Give cookies to Downloader class
                Downloader.instance!!.cookies = mCookies

                // Closing activity and return to parent
                setResult(Activity.RESULT_OK)
                finish()
            }
        }

        private fun findAccessCookies(cookies: String): Boolean {
            var ret = false
            var cookieSGL = ""     // s_gl
            var cookieGOOJF = ""   // goojf

            val parts = (cookies as CharSequence).split("; ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (part in parts) {
                if (part.trim { it <= ' ' }.startsWith("s_gl")) {
                    cookieSGL = part.trim { it <= ' ' }
                }
                if (part.trim { it <= ' ' }.startsWith("goojf")) {
                    cookieGOOJF = part.trim { it <= ' ' }
                }
            }
            if (cookieSGL.isNotEmpty() && cookieGOOJF.isNotEmpty()) {
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
        return when (id) {
            android.R.id.home -> {
                val intent = Intent(this, org.schabi.newpipe.MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                NavUtils.navigateUpTo(this, intent)
                true
            }
            else -> false
        }
    }



    companion object {
        const val TAG = "ReCaptchaActivity"

        const val RECAPTCHA_REQUEST = 10

        const val YT_URL = "https://www.youtube.com"
    }
}
