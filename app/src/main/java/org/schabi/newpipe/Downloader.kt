package org.schabi.newpipe

import android.text.TextUtils

import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.TimeUnit

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.schabi.newpipe.extractor.utils.Localization


class Downloader private constructor(builder: OkHttpClient.Builder) : org.schabi.newpipe.extractor.Downloader {
    var cookies: String? = null
    private val client: OkHttpClient = builder.readTimeout(30, TimeUnit.SECONDS)
            //.cache(new Cache(new File(context.getExternalCacheDir(), "okhttp"), 16 * 1024 * 1024))
            .build()

    /**
     * Get the size of the content that the url is pointing by firing a HEAD request.
     *
     * @param url an url pointing to the content
     * @return the size of the content, in bytes
     */
    @Throws(IOException::class)
    fun getContentLength(url: String): Long {
        var response: Response? = null
        try {
            val request = Request.Builder()
                    .head().url(url)
                    .addHeader("User-Agent", USER_AGENT)
                    .build()
            response = client.newCall(request).execute()

            return java.lang.Long.parseLong(response!!.header("Content-Length")!!)
        } catch (e: NumberFormatException) {
            throw IOException("Invalid content length", e)
        } finally {
            response?.close()
        }
    }

    /**
     * Download the text file at the supplied URL as in download(String),
     * but set the HTTP header field "Accept-Language" to the supplied string.
     *
     * @param siteUrl  the URL of the text file to return the contents of
     * @param language the language (usually a 2-character code) to set as the preferred language
     * @return the contents of the specified text file
     */
    @Throws(IOException::class, ReCaptchaException::class)
    override fun download(siteUrl: String, localization: Localization): String {
        val requestProperties = HashMap<String, String>()
        requestProperties["Accept-Language"] = localization.language
        return download(siteUrl, requestProperties)
    }

    /**
     * Download the text file at the supplied URL as in download(String),
     * but set the HTTP headers included in the customProperties map.
     *
     * @param siteUrl          the URL of the text file to return the contents of
     * @param customProperties set request header properties
     * @return the contents of the specified text file
     * @throws IOException
     */
    @Throws(IOException::class, ReCaptchaException::class)
    override fun download(siteUrl: String, customProperties: Map<String, String>): String {
        return getBody(siteUrl, customProperties)!!.string()
    }

    @Throws(IOException::class)
    fun stream(siteUrl: String): InputStream {
        try {
            return getBody(siteUrl, emptyMap())!!.byteStream()
        } catch (e: ReCaptchaException) {
            throw IOException(e.message, e.cause)
        }

    }

    @Throws(IOException::class, ReCaptchaException::class)
    private fun getBody(siteUrl: String, customProperties: Map<String, String>): ResponseBody? {
        val requestBuilder = Request.Builder()
                .method("GET", null).url(siteUrl)
                .addHeader("User-Agent", USER_AGENT)

        for ((key, value) in customProperties) {
            requestBuilder.addHeader(key, value)
        }

        if (!TextUtils.isEmpty(cookies)) {
            requestBuilder.addHeader("Cookie", cookies!!)
        }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        val body = response.body()

        if (response.code() == 429) {
            throw ReCaptchaException("reCaptcha Challenge requested")
        }

        if (body == null) {
            response.close()
            return null
        }

        return body
    }

    /**
     * Download (via HTTP) the text file located at the supplied URL, and return its contents.
     * Primarily intended for downloading web pages.
     *
     * @param siteUrl the URL of the text file to download
     * @return the contents of the specified text file
     */
    @Throws(IOException::class, ReCaptchaException::class)
    override fun download(siteUrl: String): String {
        return download(siteUrl, emptyMap())
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:43.0) Gecko/20100101 Firefox/43.0"

        var instance: Downloader? = null
            private set

        /**
         * It's recommended to call exactly once in the entire lifetime of the application.
         *
         * @param builder if null, default builder will be used
         */
        fun init(builder: OkHttpClient.Builder?): Downloader {
            instance = Downloader(builder ?: OkHttpClient.Builder())
            return instance!!
        }
    }
}
