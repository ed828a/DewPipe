/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * SubscriptionsExportService.java is part of NewPipe
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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.MainActivity.Companion.DEBUG
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.subscription.SubscriptionItem
import org.schabi.newpipe.local.subscription.ImportExportJsonHelper
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.*

class SubscriptionsExportService : BaseImportExportService() {

    private var subscription: Subscription? = null
    private var outFile: File? = null
    private var outputStream: FileOutputStream? = null

    private val subscriber: Subscriber<File>
        get() = object : Subscriber<File> {
            override fun onSubscribe(s: Subscription) {
                subscription = s
                s.request(1)
            }

            override fun onNext(file: File) {
                if (DEBUG) Log.d(TAG, "startExport() success: file = $file")
            }

            override fun onError(error: Throwable) {
                Log.e(TAG, "onError() called with: error = [$error]", error)
                handleError(error)
            }

            override fun onComplete() {
                LocalBroadcastManager.getInstance(this@SubscriptionsExportService).sendBroadcast(Intent(EXPORT_COMPLETE_ACTION))
                showToast(R.string.export_complete_toast)
                stopService()
            }
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || subscription != null) return Service.START_NOT_STICKY

        val path = intent.getStringExtra(KEY_FILE_PATH)
        if (TextUtils.isEmpty(path)) {
            stopAndReportError(IllegalStateException("Exporting to a file, but the path is empty or null"), "Exporting subscriptions")
            return Service.START_NOT_STICKY
        }

        try {
            outFile = File(path)
            outputStream = FileOutputStream(outFile)
        } catch (e: FileNotFoundException) {
            handleError(e)
            return Service.START_NOT_STICKY
        }

        startExport()

        return Service.START_NOT_STICKY
    }

    override fun getNotificationId(): Int {
        return 4567
    }

    override fun getTitle(): Int {
        return R.string.export_ongoing
    }

    override fun disposeAll() {
        super.disposeAll()
        if (subscription != null) subscription!!.cancel()
    }

    private fun startExport() {
        showToast(R.string.export_ongoing)

        subscriptionService.subscriptionTable()
                .all
                .take(1)
                .map<List<SubscriptionItem>> { subscriptionEntities ->
                    val result = ArrayList<SubscriptionItem>(subscriptionEntities.size)
                    for (entity in subscriptionEntities) {
                        result.add(SubscriptionItem(entity.serviceId, entity.url, entity.name))
                    }
                    result
                }
                .map(exportToFile())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber)
    }

    private fun exportToFile(): Function<List<SubscriptionItem>, File> {
        return Function{ subscriptionItems ->
            ImportExportJsonHelper.writeTo(subscriptionItems, outputStream, eventListener)
            outFile
        }
    }

    protected fun handleError(error: Throwable) {
        super.handleError(R.string.subscriptions_export_unsuccessful, error)
    }

    companion object {
        const val KEY_FILE_PATH = "key_file_path"

        /**
         * A [local broadcast][LocalBroadcastManager] will be made with this action when the export is successfully completed.
         */
        const val EXPORT_COMPLETE_ACTION = "org.schabi.newpipe.local.subscription.services.SubscriptionsExportService.EXPORT_COMPLETE"
    }
}
