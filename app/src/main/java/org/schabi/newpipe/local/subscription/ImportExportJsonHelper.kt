/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * ImportExportJsonHelper.java is part of NewPipe
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

package org.schabi.newpipe.local.subscription

import android.util.Log
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonSink
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor.InvalidSourceException
import org.schabi.newpipe.extractor.subscription.SubscriptionItem
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * A JSON implementation capable of importing and exporting subscriptions, it has the advantage
 * of being able to transfer subscriptions to any device.
 */
object ImportExportJsonHelper {

    val TAG = "${this::class.simpleName}@${hashCode()}"

    ///////////////////////////////////////////////////////////////////////////
    // Json implementation
    ///////////////////////////////////////////////////////////////////////////

    private const val JSON_APP_VERSION_KEY = "app_version"
    private const val JSON_APP_VERSION_INT_KEY = "app_version_int"

    private const val JSON_SUBSCRIPTIONS_ARRAY_KEY = "subscriptions"

    private const val JSON_SERVICE_ID_KEY = "service_id"
    private const val JSON_URL_KEY = "url"
    private const val JSON_NAME_KEY = "name"

    /**
     * Read a JSON source through the input stream and return the parsed subscription items.
     *
     * @param `inputStream`            the input stream (e.g. a file)
     * @param eventListener listener for the events generated
     */
    @Throws(InvalidSourceException::class)
    fun readFrom(inputStream: InputStream?, eventListener: ImportExportEventListener?): List<SubscriptionItem> {
        if (inputStream == null) throw InvalidSourceException("input is null")

        val channels = ArrayList<SubscriptionItem>()

        try {
            val parentObject = JsonParser.`object`().from(inputStream)
            val channelsArray = parentObject.getArray(JSON_SUBSCRIPTIONS_ARRAY_KEY)
            if (channelsArray == null || channelsArray.isEmpty()){
                Log.e(TAG, "Error: Channels array is null/Empty")
                return channels
            }

            eventListener?.onSizeReceived(channelsArray.size)

            for (obj in channelsArray) {
                if (obj is JsonObject) {
                    val serviceId = obj.getInt(JSON_SERVICE_ID_KEY, 0)
                    val url = obj.getString(JSON_URL_KEY)
                    val name = obj.getString(JSON_NAME_KEY)

                    if (url != null && name != null && !url.isEmpty() && !name.isEmpty()) {
                        channels.add(SubscriptionItem(serviceId, url, name))
                        eventListener?.onItemCompleted(name)
                    }
                }
            }
        } catch (e: Throwable) {
            throw InvalidSourceException("Couldn't parse json", e)
        }

        return channels
    }

    /**
     * Write the subscriptions items list as JSON to the output.
     *
     * @param items         the list of subscriptions items
     * @param out           the output stream (e.g. a file)
     * @param eventListener listener for the events generated
     */
    fun writeTo(items: List<SubscriptionItem>, out: OutputStream, eventListener: ImportExportEventListener?) {
        val writer = JsonWriter.on(out)
        writeTo(items, writer, eventListener)
        writer.done()
    }

    /**
     * @see .writeTo
     */
    fun writeTo(items: List<SubscriptionItem>, writer: JsonSink<*>, eventListener: ImportExportEventListener?) {
        eventListener?.onSizeReceived(items.size)

        writer.`object`()

        writer.value(JSON_APP_VERSION_KEY, BuildConfig.VERSION_NAME)
        writer.value(JSON_APP_VERSION_INT_KEY, BuildConfig.VERSION_CODE)

        writer.array(JSON_SUBSCRIPTIONS_ARRAY_KEY)
        for (item in items) {
            writer.`object`()
            writer.value(JSON_SERVICE_ID_KEY, item.serviceId)
            writer.value(JSON_URL_KEY, item.url)
            writer.value(JSON_NAME_KEY, item.name)
            writer.end()

            eventListener?.onItemCompleted(item.name)
        }
        writer.end()  // ending array

        writer.end()  // ending object
    }

}
