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
import org.schabi.newpipe.settings.tabs.Tab.Type.Companion.BLANK_TAB_ID
import org.schabi.newpipe.settings.tabs.Tab.Type.Companion.BOOKMARK_TAB_ID
import org.schabi.newpipe.settings.tabs.Tab.Type.Companion.CHANNEL_TAB_ID
import org.schabi.newpipe.settings.tabs.Tab.Type.Companion.FEED_TAB_ID
import org.schabi.newpipe.settings.tabs.Tab.Type.Companion.HISTORY_TAB_ID
import org.schabi.newpipe.settings.tabs.Tab.Type.Companion.KIOSK_TAB_ID
import org.schabi.newpipe.settings.tabs.Tab.Type.Companion.SUBSCRIPTION_TAB_ID
import org.schabi.newpipe.util.Constants.NO_ID_STRING
import org.schabi.newpipe.util.Constants.NO_NAME_STRING
import org.schabi.newpipe.util.Constants.NO_SERVICE_ID
import org.schabi.newpipe.util.Constants.NO_URL_STRING
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.ThemeHelper

abstract class Tab(jsonObject: JsonObject? = null) {
    init {
        jsonObject?.let {
            readDataFromJson(it)
        }
    }

    abstract val tabId: Int

    /**
     * Return a instance of the fragment that this tab represent.
     */
    abstract val fragment: Fragment

    abstract fun getTabName(context: Context): String
    @DrawableRes
    abstract fun getTabIconRes(context: Context): Int

    override fun equals(other: Any?): Boolean =
            (other is Tab) && other.javaClass == this.javaClass && other.tabId == this.tabId


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

    enum class Type(val tab: Tab) {
        BLANK(BlankTab()),
        SUBSCRIPTIONS(SubscriptionsTab()),
        FEED(FeedTab()),
        BOOKMARKS(BookmarksTab()),
        HISTORY(HistoryTab()),
        KIOSK(KioskTab()),
        CHANNEL(ChannelTab());

        val tabId: Int
            get() = tab.tabId

        companion object {
            const val BLANK_TAB_ID = 0
            const val SUBSCRIPTION_TAB_ID = 1
            const val FEED_TAB_ID = 2
            const val BOOKMARK_TAB_ID = 3
            const val HISTORY_TAB_ID = 4
            const val KIOSK_TAB_ID = 5
            const val CHANNEL_TAB_ID = 6
        }
    }

    class BlankTab : Tab() {

        override val tabId: Int
            get() = BLANK_TAB_ID

        override val fragment: BlankFragment
            get() = BlankFragment()

        override fun getTabName(context: Context): String {
            return context.getString(R.string.blank_page_summary)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_blank_page)
        }

    }

    class SubscriptionsTab : Tab() {

        override val tabId: Int
            get() = SUBSCRIPTION_TAB_ID

        override val fragment: SubscriptionFragment
            get() = SubscriptionFragment()

        override fun getTabName(context: Context): String {
            return context.getString(R.string.tab_subscriptions)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_channel)
        }
    }

    class FeedTab : Tab() {

        override val tabId: Int
            get() = FEED_TAB_ID

        override val fragment: FeedFragment
            get() = FeedFragment()

        override fun getTabName(context: Context): String {
            return context.getString(R.string.fragment_whats_new)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.rss)
        }
    }

    class BookmarksTab : Tab() {

        override val tabId: Int
            get() = BOOKMARK_TAB_ID

        override val fragment: BookmarkFragment
            get() = BookmarkFragment()

        override fun getTabName(context: Context): String {
            return context.getString(R.string.tab_bookmarks)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_bookmark)
        }
    }

    class HistoryTab : Tab() {

        override val tabId: Int
            get() = HISTORY_TAB_ID

        override val fragment: StatisticsPlaylistFragment
            get() = StatisticsPlaylistFragment()

        override fun getTabName(context: Context): String {
            return context.getString(R.string.title_activity_history)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.history)
        }
    }

    class KioskTab : Tab {

        var kioskServiceId: Int = 0
            private set
        var kioskId: String? = null
            private set

        override val tabId: Int
            get() = KIOSK_TAB_ID

        override val fragment: KioskFragment
            @Throws(ExtractionException::class)
            get() = KioskFragment.getInstance(kioskServiceId, kioskId!!)

//        constructor() : this(-1, "<no-id>") {}

        constructor(kioskServiceId: Int = NO_SERVICE_ID, kioskId: String = "<no-id>") {
            this.kioskServiceId = kioskServiceId
            this.kioskId = kioskId
        }

        constructor(jsonObject: JsonObject) : super(jsonObject) {}

        override fun getTabName(context: Context): String {
            val id = kioskId ?: ""
            return KioskTranslator.getTranslatedKioskName(id, context)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            val id = kioskId ?: ""
            val kioskIcon = KioskTranslator.getKioskIcons(id, context)

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
            kioskServiceId = jsonObject.getInt(JSON_KIOSK_SERVICE_ID_KEY, NO_SERVICE_ID)
            kioskId = jsonObject.getString(JSON_KIOSK_ID_KEY, NO_ID_STRING)
        }

        companion object {
            private const val JSON_KIOSK_SERVICE_ID_KEY = "service_id"
            private const val JSON_KIOSK_ID_KEY = "kiosk_id"
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
            get() = CHANNEL_TAB_ID

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
            channelServiceId = jsonObject.getInt(JSON_CHANNEL_SERVICE_ID_KEY, NO_SERVICE_ID)
            channelUrl = jsonObject.getString(JSON_CHANNEL_URL_KEY, NO_URL_STRING)
            channelName = jsonObject.getString(JSON_CHANNEL_NAME_KEY, NO_NAME_STRING)
        }

        companion object {
            private const val JSON_CHANNEL_SERVICE_ID_KEY = "channel_service_id"
            private const val JSON_CHANNEL_URL_KEY = "channel_url"
            private const val JSON_CHANNEL_NAME_KEY = "channel_name"
        }
    }

    companion object {
        private val TAG = Tab::class.simpleName

        ///////////////////////////////////////////////////////////////////////////
        // JSON Handling
        ///////////////////////////////////////////////////////////////////////////

        private const val JSON_TAB_ID_KEY = "tab_id"

        ///////////////////////////////////////////////////////////////////////////
        // Tab Handling
        ///////////////////////////////////////////////////////////////////////////

        fun from(jsonObject: JsonObject): Tab? {
            val tabId = jsonObject.getInt(Tab.JSON_TAB_ID_KEY, NO_SERVICE_ID)

            return if (tabId == NO_SERVICE_ID) {
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
                when (tabId) {
                    KIOSK_TAB_ID -> return KioskTab(jsonObject)
                    CHANNEL_TAB_ID -> return ChannelTab(jsonObject)
                }
            }

            return type.tab
        }
    }
}
