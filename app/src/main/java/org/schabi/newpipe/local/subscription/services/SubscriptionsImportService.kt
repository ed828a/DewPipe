/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * SubscriptionsImportService.java is part of NewPipe
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

package org.schabi.newpipe.local.subscription.services

import android.app.Service
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.text.TextUtils
import android.util.Log

import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.subscription.SubscriptionItem
import org.schabi.newpipe.local.subscription.ImportExportJsonHelper
import org.schabi.newpipe.util.Constants
import org.schabi.newpipe.util.ExtractorHelper

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList

import io.reactivex.Flowable
import io.reactivex.Notification
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers

import org.schabi.newpipe.MainActivity.Companion.DEBUG

class SubscriptionsImportService : BaseImportExportService() {

    private var subscription: Subscription? = null
    private var currentMode: Int = 0
    private var currentServiceId: Int = 0

    private var channelUrl: String? = null
    private var inputStream: InputStream? = null

    private val subscriber: Subscriber<List<SubscriptionEntity>>
        get() = object : Subscriber<List<SubscriptionEntity>> {

            override fun onSubscribe(s: Subscription) {
                subscription = s
                s.request(java.lang.Long.MAX_VALUE)
            }

            override fun onNext(successfulInserted: List<SubscriptionEntity>) {
                if (DEBUG) Log.d(TAG, "startImport() " + successfulInserted.size + " items successfully inserted into the database")
            }

            override fun onError(error: Throwable) {
                handleError(error)
            }

            override fun onComplete() {
                LocalBroadcastManager.getInstance(this@SubscriptionsImportService).sendBroadcast(Intent(IMPORT_COMPLETE_ACTION))
                showToast(R.string.import_complete_toast)
                stopService()
            }
        }

    private fun getNotificationsConsumer(): Consumer<Notification<ChannelInfo>> = Consumer { notification ->
        if (notification.isOnNext) {
            val name = notification.value?.name
            eventListener.onItemCompleted(if (!TextUtils.isEmpty(name)) name else "")
        } else if (notification.isOnError) {
            val error = notification.error
            val cause = error?.cause
            if (error is IOException) {
                throw error
            } else if (cause != null && cause is IOException) {
                throw cause
            }

            eventListener.onItemCompleted("")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || subscription != null) return Service.START_NOT_STICKY

        currentMode = intent.getIntExtra(KEY_MODE, -1)
        currentServiceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, Constants.NO_SERVICE_ID)

        if (currentMode == CHANNEL_URL_MODE) {
            channelUrl = intent.getStringExtra(KEY_VALUE)
        } else {
            val filePath = intent.getStringExtra(KEY_VALUE)
            if (TextUtils.isEmpty(filePath)) {
                stopAndReportError(IllegalStateException("Importing from input stream, but file path is empty or null"), "Importing subscriptions")
                return Service.START_NOT_STICKY
            }

            try {
                inputStream = FileInputStream(File(filePath))
            } catch (e: FileNotFoundException) {
                handleError(e)
                return Service.START_NOT_STICKY
            }

        }

        if (currentMode == -1 || currentMode == CHANNEL_URL_MODE && channelUrl == null) {
            val errorDescription = "Some important field is null or in illegal state: currentMode=[$currentMode], channelUrl=[$channelUrl], inputStream=[$inputStream]"
            stopAndReportError(IllegalStateException(errorDescription), "Importing subscriptions")
            return Service.START_NOT_STICKY
        }

        startImport()
        return Service.START_NOT_STICKY
    }

    override fun getNotificationId(): Int {
        return 4568
    }

    override fun getTitle(): Int {
        return R.string.import_ongoing
    }

    override fun disposeAll() {
        super.disposeAll()
        if (subscription != null) subscription!!.cancel()
    }

    private fun startImport() {
        showToast(R.string.import_ongoing)

        var flowable: Flowable<List<SubscriptionItem>>? = null
        when (currentMode) {
            CHANNEL_URL_MODE -> flowable = importFromChannelUrl()
            INPUT_STREAM_MODE -> flowable = importFromInputStream()
            PREVIOUS_EXPORT_MODE -> flowable = importFromPreviousExport()
        }

        if (flowable == null) {
            val message = "Flowable given by \"importFrom\" is null (current mode: $currentMode)"
            stopAndReportError(IllegalStateException(message), "Importing subscriptions")
            return
        }

        flowable.doOnNext { subscriptionItems -> eventListener.onSizeReceived(subscriptionItems.size) }
                .flatMap { Flowable.fromIterable(it) }
                .parallel(PARALLEL_EXTRACTIONS)
                .runOn(Schedulers.io())
                .map { subscriptionItem ->
                    try {
                        return@map Notification.createOnNext(
                                ExtractorHelper.getChannelInfo(subscriptionItem.serviceId, subscriptionItem.url, true)
                                        .blockingGet())
                    } catch (e: Throwable) {
                        return@map Notification.createOnError<ChannelInfo>(e)
                    }
                }
                .sequential()

                .observeOn(Schedulers.io())
                .doOnNext(getNotificationsConsumer())
                .buffer(BUFFER_COUNT_BEFORE_INSERT)
                .map(upsertBatch())

                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber)
    }

    private fun upsertBatch(): Function<List<Notification<ChannelInfo>>, List<SubscriptionEntity>> {
        return Function{ notificationList ->
            val infoList = ArrayList<ChannelInfo>(notificationList.size)
            for (notification in notificationList) {
                if (notification.isOnNext) infoList.add(notification.value!!)
            }

            subscriptionService.upsertAll(infoList)
        }
    }

    private fun importFromChannelUrl(): Flowable<List<SubscriptionItem>> {
        return Flowable.fromCallable {
            NewPipe.getService(currentServiceId)
                    .subscriptionExtractor
                    .fromChannelUrl(channelUrl)
        }
    }

    private fun importFromInputStream(): Flowable<List<SubscriptionItem>> {
        return Flowable.fromCallable {
            NewPipe.getService(currentServiceId)
                    .subscriptionExtractor
                    .fromInputStream(inputStream)
        }
    }

    private fun importFromPreviousExport(): Flowable<List<SubscriptionItem>> {
        return Flowable.fromCallable { ImportExportJsonHelper.readFrom(inputStream, null) }
    }

    protected fun handleError(error: Throwable) {
        super.handleError(R.string.subscriptions_import_unsuccessful, error)
    }

    companion object {
        const val CHANNEL_URL_MODE = 0
        const val INPUT_STREAM_MODE = 1
        const val PREVIOUS_EXPORT_MODE = 2
        const val KEY_MODE = "key_mode"
        const val KEY_VALUE = "key_value"

        /**
         * A [local broadcast][LocalBroadcastManager] will be made with this action when the import is successfully completed.
         */
        const val IMPORT_COMPLETE_ACTION = "org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.IMPORT_COMPLETE"

        ///////////////////////////////////////////////////////////////////////////
        // Imports
        ///////////////////////////////////////////////////////////////////////////

        /**
         * How many extractions running in parallel.
         */
        const val PARALLEL_EXTRACTIONS = 8

        /**
         * Number of items to buffer to mass-insert in the subscriptions table, this leads to
         * a better performance as we can then use db transactions.
         */
        const val BUFFER_COUNT_BEFORE_INSERT = 50
    }
}