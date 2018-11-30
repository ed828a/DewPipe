/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * Extractors.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.widget.Toast
import io.reactivex.Maybe
import io.reactivex.Single
import org.schabi.newpipe.BuildConfig.DEBUG
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.ReCaptchaActivity
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.UserAction
import java.io.IOException
import java.io.InterruptedIOException

object ExtractorHelper {
    private val TAG = ExtractorHelper::class.java.simpleName
    private val cache = InfoCache.instance

    private fun checkServiceId(serviceId: Int) {
        if (serviceId == Constants.NO_SERVICE_ID) {
            throw IllegalArgumentException("serviceId is NO_SERVICE_ID")
        }
    }

    fun searchFor(serviceId: Int,
                  searchString: String?,
                  contentFilter: List<String?>,
                  sortFilter: String): Single<SearchInfo> {
        checkServiceId(serviceId)
        return Single.fromCallable {
            SearchInfo.getInfo(NewPipe.getService(serviceId),
                    NewPipe.getService(serviceId)
                            .searchQHFactory
                            .fromQuery(searchString, contentFilter, sortFilter))
        }
    }

    fun getMoreSearchItems(serviceId: Int,
                           searchString: String?,
                           contentFilter: List<String?>,
                           sortFilter: String,
                           pageUrl: String?): Single<InfoItemsPage<*>> {
        checkServiceId(serviceId)
        return Single.fromCallable {
            SearchInfo.getMoreItems(NewPipe.getService(serviceId),
                    NewPipe.getService(serviceId)
                            .searchQHFactory
                            .fromQuery(searchString, contentFilter, sortFilter),
                    pageUrl)
        }

    }

    fun suggestionsFor(serviceId: Int,
                       query: String): Single<List<String>> {
        checkServiceId(serviceId)
        return Single.fromCallable {
            NewPipe.getService(serviceId)
                    .suggestionExtractor
                    .suggestionList(query)
        }
    }

    fun getStreamInfo(serviceId: Int,
                      url: String,
                      forceLoad: Boolean): Single<StreamInfo> {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, url, Single.fromCallable { StreamInfo.getInfo(NewPipe.getService(serviceId), url) })

    }

    fun getChannelInfo(serviceId: Int,
                       url: String,
                       forceLoad: Boolean): Single<ChannelInfo> {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, url, Single.fromCallable { ChannelInfo.getInfo(NewPipe.getService(serviceId), url) })
    }

    fun getMoreChannelItems(serviceId: Int,
                            url: String,
                            nextStreamsUrl: String?): Single<InfoItemsPage<*>> {
        checkServiceId(serviceId)
        return Single.fromCallable { ChannelInfo.getMoreItems(NewPipe.getService(serviceId), url, nextStreamsUrl) }
    }

    fun getPlaylistInfo(serviceId: Int,
                        url: String,
                        forceLoad: Boolean): Single<PlaylistInfo> {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, url, Single.fromCallable { PlaylistInfo.getInfo(NewPipe.getService(serviceId), url) })
    }

    fun getMorePlaylistItems(serviceId: Int,
                             url: String,
                             nextStreamsUrl: String?): Single<InfoItemsPage<*>> {
        checkServiceId(serviceId)
        return Single.fromCallable { PlaylistInfo.getMoreItems(NewPipe.getService(serviceId), url, nextStreamsUrl) }
    }

    fun getKioskInfo(serviceId: Int,
                     url: String,
                     forceLoad: Boolean): Single<KioskInfo> {
        return checkCache(forceLoad, serviceId, url, Single.fromCallable { KioskInfo.getInfo(NewPipe.getService(serviceId), url) })
    }

    fun getMoreKioskItems(serviceId: Int,
                          url: String,
                          nextStreamsUrl: String): Single<InfoItemsPage<*>> {
        return Single.fromCallable {
            KioskInfo.getMoreItems(NewPipe.getService(serviceId),
                    url, nextStreamsUrl)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Check if we can load it from the cache (forceLoad parameter), if we can't,
     * load from the network (Single loadFromNetwork)
     * and put the results in the cache.
     */
    private fun <I : Info> checkCache(forceLoad: Boolean,
                                      serviceId: Int,
                                      url: String,
                                      loadFromNetwork: Single<I>): Single<I> {
        var loadFromNetwork = loadFromNetwork
        checkServiceId(serviceId)
        loadFromNetwork = loadFromNetwork.doOnSuccess { info -> cache.putInfo(serviceId, url, info) }

        val load: Single<I>
        load = if (forceLoad) {
            cache.removeInfo(serviceId, url)
            loadFromNetwork
        } else {
            Maybe.concat(ExtractorHelper.loadFromCache(serviceId, url),
                    loadFromNetwork.toMaybe())
                    .firstElement() //Take the first valid
                    .toSingle()
        }

        return load
    }

    /**
     * Default implementation uses the [InfoCache] to get cached results
     */
    private fun <I : Info> loadFromCache(serviceId: Int, url: String): Maybe<I> {
        checkServiceId(serviceId)
        return Maybe.defer {
            val info = cache.getFromKey(serviceId, url) as I?
            when (info){
                null -> Maybe.empty<I>()
                else -> {
                    if (DEBUG) Log.d(TAG, "loadFromCache() called, info > $info")
                    Maybe.just(info)
                }
            }
        }
    }

    /**
     * A simple and general error handler that show a Toast for known exceptions, and for others, opens the report error activity with the (optional) error message.
     */
    fun handleGeneralException(context: Context, serviceId: Int, url: String?, exception: Throwable, userAction: UserAction, optionalErrorMessage: String?) {
        val handler = Handler(context.mainLooper)

        handler.post {
            when (exception) {
                is ReCaptchaException -> {
                    Toast.makeText(context, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show()
                    // Starting ReCaptcha Challenge Activity
                    val intent = Intent(context, ReCaptchaActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                is IOException -> Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show()
                is YoutubeStreamExtractor.GemaException -> Toast.makeText(context, R.string.blocked_by_gema, Toast.LENGTH_LONG).show()
                is ContentNotAvailableException -> Toast.makeText(context, R.string.content_not_available, Toast.LENGTH_LONG).show()
                else -> {

                    val errorId = when (exception) {
                        is YoutubeStreamExtractor.DecryptException -> R.string.youtube_signature_decryption_error
                        is ParsingException -> R.string.parsing_error
                        else -> R.string.general_error
                    }

                    ErrorActivity.reportError(
                            handler,
                            context,
                            exception,
                            MainActivity::class.java,
                            null,
                            ErrorActivity.ErrorInfo.make(userAction,
                                    if (serviceId == -1) "none" else NewPipe.getNameOfService(serviceId), url + (optionalErrorMessage
                                    ?: ""), errorId))
                }
            }
        }
    }

    /**
     * Check if throwable have the cause that can be assignable from the causes to check.
     *
     * @see Class.isAssignableFrom
     */
    fun hasAssignableCauseThrowable(throwable: Throwable?,
                                    vararg causesToCheck: Class<*>): Boolean {
        // Check if getCause is not the same as cause (the getCause is already the root),
        // as it will cause a infinite loop if it is
        var cause: Throwable
        var getCause = throwable

        // Check if throwable is a subclass of any of the filtered classes
        val throwableClass = throwable?.javaClass
        for (causesEl in causesToCheck) {
            if (causesEl.isAssignableFrom(throwableClass)) {
                return true
            }
        }

        // Iteratively checks if the root cause of the throwable is a subclass of the filtered class
        val temp = throwable?.cause
        temp?.let {
            cause = it
            while (getCause !== cause) {
                getCause = cause
                val causeClass = cause.javaClass
                for (causesEl in causesToCheck) {
                    if (causesEl.isAssignableFrom(causeClass)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * Check if throwable have the exact cause from one of the causes to check.
     */
    private fun hasExactCauseThrowable(throwable: Throwable,
                                       vararg causesToCheck: Class<*>): Boolean {
        // Check if getCause is not the same as cause (the getCause is already the root),
        // as it will cause a infinite loop if it is
        var cause: Throwable
        var getCause = throwable

        for (causesEl in causesToCheck) {
            if (throwable.javaClass == causesEl) {
                return true
            }
        }
        val temp = throwable.cause!!
        throwable.cause?.let {
            cause = throwable.cause!!
            while (getCause !== cause) {
                getCause = cause
                for (causesEl in causesToCheck) {
                    if (cause.javaClass == causesEl) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Check if throwable have Interrupted* exception as one of its causes.
     */
    fun isInterruptedCaused(throwable: Throwable): Boolean {
        return ExtractorHelper.hasExactCauseThrowable(throwable,
                InterruptedIOException::class.java,
                InterruptedException::class.java)
    }
}
