package org.schabi.newpipe.settings.tabs

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.settings.tabs.Tab.Type
import java.util.*

/**
 * Class to get a JSON representation of a list of tabs, and the other way around.
 */
object TabsJsonHelper {
    private const val JSON_TABS_ARRAY_KEY = "tabs"

    internal val FALLBACK_INITIAL_TABS_LIST = Collections.unmodifiableList(Arrays.asList(
            Tab.KioskTab(YouTube.serviceId, "Trending"),
            Type.SUBSCRIPTIONS.tab,
            Type.BOOKMARKS.tab
    ))

    class InvalidJsonException : Exception {
        private constructor() : super() {}

        constructor(message: String) : super(message) {}

        constructor(cause: Throwable) : super(cause) {}
    }

    /**
     * Try to reads the passed JSON and returns the list of tabs if no error were encountered.
     *
     *
     * If the JSON is null or empty, or the list of tabs that it represents is empty, the
     * [fallback list][.FALLBACK_INITIAL_TABS_LIST] will be returned.
     *
     *
     * Tabs with invalid ids (i.e. not in the [Tab.Type] enum) will be ignored.
     *
     * @param tabsJson a JSON string got from [.getJsonToSave].
     * @return a list of [tabs][Tab].
     * @throws InvalidJsonException if the JSON string is not valid
     */
    @Throws(TabsJsonHelper.InvalidJsonException::class)
    fun getTabsFromJson(tabsJson: String?): List<Tab> {
        if (tabsJson == null || tabsJson.isEmpty()) {
            return FALLBACK_INITIAL_TABS_LIST
        }

        val returnTabs = ArrayList<Tab>()

        val outerJsonObject: JsonObject
        try {
            outerJsonObject = JsonParser.`object`().from(tabsJson)
            val tabsArray = outerJsonObject.getArray(JSON_TABS_ARRAY_KEY)
                    ?: throw InvalidJsonException("JSON doesn't contain \"$JSON_TABS_ARRAY_KEY\" array")

            for (o in tabsArray) {
                if (o !is JsonObject) continue

                val tab = Tab.from(o)

                if (tab != null) {
                    returnTabs.add(tab)
                }
            }
        } catch (e: JsonParserException) {
            throw InvalidJsonException(e)
        }

        return if (returnTabs.isEmpty()) {
            FALLBACK_INITIAL_TABS_LIST
        } else returnTabs

    }

    /**
     * Get a JSON representation from a list of tabs.
     *
     * @param tabList a list of [tabs][Tab].
     * @return a JSON string representing the list of tabs
     */
    fun getJsonToSave(tabList: List<Tab>?): String {
        val jsonWriter = JsonWriter.string()
        jsonWriter.`object`()

        jsonWriter.array(JSON_TABS_ARRAY_KEY)
        if (tabList != null)
            for (tab in tabList) {
                tab.writeJsonOn(jsonWriter)
            }
        jsonWriter.end()

        jsonWriter.end()
        return jsonWriter.done()
    }
}