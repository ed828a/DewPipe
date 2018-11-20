package org.schabi.newpipe.settings.tabs

import android.content.Context
import android.support.annotation.DrawableRes
import android.support.v4.app.Fragment
import android.util.Log

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonSink

import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.fragments.BlankFragment
import org.schabi.newpipe.fragments.list.channel.ChannelFragment
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment
import org.schabi.newpipe.local.bookmark.BookmarkFragment
import org.schabi.newpipe.local.feed.FeedFragment
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment
import org.schabi.newpipe.local.subscription.SubscriptionFragment
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.ThemeHelper

abstract class Tab {
    protected val TAG = javaClass.simpleName + "@" + Integer.toHexString(hashCode())

    abstract val tabId: Int

    /**
     * Return a instance of the fragment that this tab represent.
     */
    abstract val fragment: Fragment

    internal constructor() {}

    internal constructor(jsonObject: JsonObject) {
        readDataFromJson(jsonObject)
    }

    abstract fun getTabName(context: Context): String
    @DrawableRes
    abstract fun getTabIconRes(context: Context): Int

    override fun equals(obj: Any?): Boolean {
        return (obj is Tab && obj.javaClass == this.javaClass
                && obj.tabId == this.tabId)
    }

    fun writeJsonOn(jsonSink: JsonSink<*>) {
        jsonSink.`object`()

        jsonSink.value(JSON_TAB_ID_KEY, tabId)
        writeDataToJson(jsonSink)

        jsonSink.end()
    }

    protected open fun writeDataToJson(writerSink: JsonSink<*>) {
        // No-op
    }

    protected open fun readDataFromJson(jsonObject: JsonObject) {
        // No-op
    }

    ///////////////////////////////////////////////////////////////////////////
    // Implementations
    ///////////////////////////////////////////////////////////////////////////

    enum class Type private constructor(val tab: Tab) {
        BLANK(BlankTab()),
        SUBSCRIPTIONS(SubscriptionsTab()),
        FEED(FeedTab()),
        BOOKMARKS(BookmarksTab()),
        HISTORY(HistoryTab()),
        KIOSK(KioskTab()),
        CHANNEL(ChannelTab());

        val tabId: Int
            get() = tab.tabId
    }

    class BlankTab : Tab() {

        override val tabId: Int
            get() = ID

        override val fragment: BlankFragment
            get() = BlankFragment()

        override fun getTabName(context: Context): String {
            return "NewPipe" //context.getString(R.string.blank_page_summary);
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_blank_page)
        }

        companion object {
            const val ID = 0
        }
    }

    class SubscriptionsTab : Tab() {

        override val tabId: Int
            get() = ID

        override val fragment: SubscriptionFragment
            get() = SubscriptionFragment()

        override fun getTabName(context: Context): String {
            return context.getString(R.string.tab_subscriptions)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_channel)
        }

        companion object {
            val ID = 1
        }

    }

    class FeedTab : Tab() {

        override val tabId: Int
            get() = ID

        override val fragment: FeedFragment
            get() = FeedFragment()

        override fun getTabName(context: Context): String {
            return context.getString(R.string.fragment_whats_new)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.rss)
        }

        companion object {
            val ID = 2
        }
    }

    class BookmarksTab : Tab() {

        override val tabId: Int
            get() = ID

        override val fragment: BookmarkFragment
            get() = BookmarkFragment()

        override fun getTabName(context: Context): String {
            return context.getString(R.string.tab_bookmarks)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_bookmark)
        }

        companion object {
            val ID = 3
        }
    }

    class HistoryTab : Tab() {

        override val tabId: Int
            get() = ID

        override val fragment: StatisticsPlaylistFragment
            get() = StatisticsPlaylistFragment()

        override fun getTabName(context: Context): String {
            return context.getString(R.string.title_activity_history)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.history)
        }

        companion object {
            val ID = 4
        }
    }

    class KioskTab : Tab {

        var kioskServiceId: Int = 0
            private set
        var kioskId: String? = null
            private set

        override val tabId: Int
            get() = ID

        override val fragment: KioskFragment
            @Throws(ExtractionException::class)
            get() = KioskFragment.getInstance(kioskServiceId, kioskId!!)

        constructor() : this(-1, "<no-id>") {}

        constructor(kioskServiceId: Int, kioskId: String) {
            this.kioskServiceId = kioskServiceId
            this.kioskId = kioskId
        }

        constructor(jsonObject: JsonObject) : super(jsonObject) {}

        override fun getTabName(context: Context): String {
            return KioskTranslator.getTranslatedKioskName(kioskId!!, context)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            val kioskIcon = KioskTranslator.getKioskIcons(kioskId!!, context)

            if (kioskIcon <= 0) {
                throw IllegalStateException("Kiosk ID is not valid: \"$kioskId\"")
            }

            return kioskIcon
        }

        override fun writeDataToJson(writerSink: JsonSink<*>) {
            writerSink.value(JSON_KIOSK_SERVICE_ID_KEY, kioskServiceId)
                    .value(JSON_KIOSK_ID_KEY, kioskId)
        }

        override fun readDataFromJson(jsonObject: JsonObject) {
            kioskServiceId = jsonObject.getInt(JSON_KIOSK_SERVICE_ID_KEY, -1)
            kioskId = jsonObject.getString(JSON_KIOSK_ID_KEY, "<no-id>")
        }

        companion object {
            val ID = 5

            private val JSON_KIOSK_SERVICE_ID_KEY = "service_id"
            private val JSON_KIOSK_ID_KEY = "kiosk_id"
        }
    }

    class ChannelTab : Tab {

        var channelServiceId: Int = 0
            private set
        var channelUrl: String? = null
            private set
        var channelName: String? = null
            private set

        override val tabId: Int
            get() = ID

        override val fragment: ChannelFragment
            get() {
                Log.d(TAG, "ChannelTab::getFragment called")
                return ChannelFragment.getInstance(channelServiceId, channelUrl!!, channelName!!)
            }

        constructor() : this(-1, "<no-url>", "<no-name>") {}

        constructor(channelServiceId: Int, channelUrl: String, channelName: String) {
            this.channelServiceId = channelServiceId
            this.channelUrl = channelUrl
            this.channelName = channelName
        }

        constructor(jsonObject: JsonObject) : super(jsonObject) {}

        override fun getTabName(context: Context): String {
            return channelName!!
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_channel)
        }

        override fun writeDataToJson(writerSink: JsonSink<*>) {
            writerSink.value(JSON_CHANNEL_SERVICE_ID_KEY, channelServiceId)
                    .value(JSON_CHANNEL_URL_KEY, channelUrl)
                    .value(JSON_CHANNEL_NAME_KEY, channelName)
        }

        override fun readDataFromJson(jsonObject: JsonObject) {
            channelServiceId = jsonObject.getInt(JSON_CHANNEL_SERVICE_ID_KEY, -1)
            channelUrl = jsonObject.getString(JSON_CHANNEL_URL_KEY, "<no-url>")
            channelName = jsonObject.getString(JSON_CHANNEL_NAME_KEY, "<no-name>")
        }

        companion object {
            const val ID = 6

            private const val JSON_CHANNEL_SERVICE_ID_KEY = "channel_service_id"
            private const val JSON_CHANNEL_URL_KEY = "channel_url"
            private const val JSON_CHANNEL_NAME_KEY = "channel_name"
        }
    }

    companion object {

        ///////////////////////////////////////////////////////////////////////////
        // JSON Handling
        ///////////////////////////////////////////////////////////////////////////

        private const val JSON_TAB_ID_KEY = "tab_id"

        ///////////////////////////////////////////////////////////////////////////
        // Tab Handling
        ///////////////////////////////////////////////////////////////////////////

        fun from(jsonObject: JsonObject): Tab? {
            val tabId = jsonObject.getInt(Tab.JSON_TAB_ID_KEY, -1)

            return if (tabId == -1) {
                null
            } else from(tabId, jsonObject)

        }

        fun from(tabId: Int): Tab? {
            return from(tabId, null)
        }

        fun typeFrom(tabId: Int): Type? {
            for (available in Type.values()) {
                if (available.tabId == tabId) {
                    return available
                }
            }
            return null
        }

        private fun from(tabId: Int, jsonObject: JsonObject?): Tab? {
            val type = typeFrom(tabId) ?: return null

            if (jsonObject != null) {
                when (type) {
                    Tab.Type.KIOSK -> return KioskTab(jsonObject)
                    Tab.Type.CHANNEL -> return ChannelTab(jsonObject)
                }
            }

            return type.tab
        }
    }
}
