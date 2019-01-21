package org.schabi.newpipe.settings.tabs

import android.util.Log
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.settings.tabs.Tab.Type
import java.util.*

/**
 * Class to get a JSON representation of a list of tabs, and the other way around.
 *
 * JsonParser/JsonWriter: refer to https://github.com/mmastrac/nanojson
 */
object TabsJsonHelper {
    private const val TAG = "TabsJsonHelper"
    private const val JSON_TABS_ARRAY_KEY = "tabs"

    val FALLBACK_INITIAL_TABS_LIST: List<Tab> = Collections.unmodifiableList(Arrays.asList(
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
     * @param tabsJson a JSON string got getTabFrom [.getJsonToSave].
     * @return a list of [tabs][Tab].
     * @throws InvalidJsonException if the JSON string is not valid
     */
    @Throws(TabsJsonHelper.InvalidJsonException::class)
    fun getTabsFromJson(tabsJson: String?): List<Tab> {
        if (tabsJson == null || tabsJson.isEmpty()) {
            return FALLBACK_INITIAL_TABS_LIST
        }

        val returnTabs = ArrayList<Tab>()

//        val outerJsonObject: JsonObject
        try {
            val outerJsonObject = JsonParser.`object`().from(tabsJson)
                    ?: throw InvalidJsonException("JSON doesn't contain Json Object")
            val tabsArray = outerJsonObject.getArray(JSON_TABS_ARRAY_KEY)
                    ?: throw InvalidJsonException("JSON doesn't contain \"$JSON_TABS_ARRAY_KEY\" array")
            Log.d(TAG, "tabsArray: $tabsArray")

            for (obj in tabsArray) {
                if (obj is JsonObject){
                    val tab = Tab.getTabFrom(obj)
                    if (tab != null) {
                        returnTabs.add(tab)
                    }
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
     * Get a JSON representation getTabFrom a list of tabs.
     * convert a List<TAB> to Json
     * @param tabList a list of [tabs][Tab].
     * @return a JSON string representing the list of tabs
     */
    fun getJsonToSave(tabList: List<Tab>?): String {
        val jsonWriter = JsonWriter.string()
        jsonWriter.`object`().array(JSON_TABS_ARRAY_KEY)

        if (tabList != null) {
            for (tab in tabList) {
                tab.writeJsonOn(jsonWriter)
            }
            jsonWriter.end()   // close array
        }

        jsonWriter.end() // close object
        return jsonWriter.done()
    }
}