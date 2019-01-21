package org.schabi.newpipe.settings.tabs

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException

import org.junit.Test

import java.util.Arrays

import java.util.Objects.requireNonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

class TabsJsonHelperTest {

    @Test
    @Throws(TabsJsonHelper.InvalidJsonException::class)
    fun testEmptyAndNullRead() {
        val emptyTabsJson = "{\"$JSON_TABS_ARRAY_KEY\":[]}"
        var items = TabsJsonHelper.getTabsFromJson(emptyTabsJson)
        // Check if instance is the same
        assertTrue(items === TabsJsonHelper.FALLBACK_INITIAL_TABS_LIST)

        val nullSource: String? = null
        items = TabsJsonHelper.getTabsFromJson(nullSource)
        assertTrue(items === TabsJsonHelper.FALLBACK_INITIAL_TABS_LIST)
    }

    @Test
    @Throws(TabsJsonHelper.InvalidJsonException::class)
    fun testInvalidIdRead() {
        val blankTabId = Tab.Type.BLANK.tabId
        val emptyTabsJson = "{\"" + JSON_TABS_ARRAY_KEY + "\":[" +
                "{\"" + JSON_TAB_ID_KEY + "\":" + blankTabId + "}," +
                "{\"" + JSON_TAB_ID_KEY + "\":" + 12345678 + "}" +
                "]}"
        val items = TabsJsonHelper.getTabsFromJson(emptyTabsJson)

        assertEquals("Should ignore the tab with invalid id", 1, items.size.toLong())
        assertEquals(blankTabId.toLong(), items[0].tabId.toLong())
    }

    @Test
    fun testInvalidRead() {
        val invalidList = Arrays.asList(
                "{\"notTabsArray\":[]}",
                "{invalidJSON]}",
                "{}"
        )

        for (invalidContent in invalidList) {
            try {
                TabsJsonHelper.getTabsFromJson(invalidContent)

                fail("didn't throw exception")
            } catch (e: Exception) {
                val isExpectedException = e is TabsJsonHelper.InvalidJsonException
                assertTrue("\"" + e.javaClass.simpleName + "\" is not the expected exception", isExpectedException)
            }

        }
    }

    @Test
    @Throws(JsonParserException::class)
    fun testEmptyAndNullSave() {
        val emptyList = emptyList<Tab>()
        var returnedJson = TabsJsonHelper.getJsonToSave(emptyList)
        assertTrue(isTabsArrayEmpty(returnedJson))

        val nullList: List<Tab>? = null
        returnedJson = TabsJsonHelper.getJsonToSave(nullList)
        assertTrue(isTabsArrayEmpty(returnedJson))
    }

    @Throws(JsonParserException::class)
    private fun isTabsArrayEmpty(returnedJson: String): Boolean {
        val jsonObject = JsonParser.`object`().from(returnedJson)
        assertTrue(jsonObject.containsKey(JSON_TABS_ARRAY_KEY))
        return jsonObject.getArray(JSON_TABS_ARRAY_KEY).size == 0
    }

    @Test
    @Throws(JsonParserException::class)
    fun testSaveAndReading() {
        // Saving
        val blankTab = Tab.BlankTab()
        val subscriptionsTab = Tab.SubscriptionsTab()
        val channelTab = Tab.ChannelTab(666, "https://example.org", "testName")
        val kioskTab = Tab.KioskTab(123, "trending_key")

        val tabs = Arrays.asList(blankTab, subscriptionsTab, channelTab, kioskTab)
        val returnedJson = TabsJsonHelper.getJsonToSave(tabs)

        // Reading
        val jsonObject = JsonParser.`object`().from(returnedJson)
        assertTrue(jsonObject.containsKey(JSON_TABS_ARRAY_KEY))
        val tabsFromArray = jsonObject.getArray(JSON_TABS_ARRAY_KEY)

        assertEquals(tabs.size.toLong(), tabsFromArray.size.toLong())

        val blankTabFromReturnedJson = requireNonNull<Tab.BlankTab>(Tab.getTabFrom(tabsFromArray[0] as JsonObject) as Tab.BlankTab?)
        assertEquals(blankTab.tabId.toLong(), blankTabFromReturnedJson.tabId.toLong())

        val subscriptionsTabFromReturnedJson = requireNonNull<Tab.SubscriptionsTab>(Tab.getTabFrom(tabsFromArray[1] as JsonObject) as Tab.SubscriptionsTab?)
        assertEquals(subscriptionsTab.tabId.toLong(), subscriptionsTabFromReturnedJson.tabId.toLong())

        val channelTabFromReturnedJson = requireNonNull<Tab.ChannelTab>(Tab.getTabFrom(tabsFromArray[2] as JsonObject) as Tab.ChannelTab?)
        assertEquals(channelTab.tabId.toLong(), channelTabFromReturnedJson.tabId.toLong())
        assertEquals(channelTab.channelServiceId.toLong(), channelTabFromReturnedJson.channelServiceId.toLong())
        assertEquals(channelTab.channelUrl, channelTabFromReturnedJson.channelUrl)
        assertEquals(channelTab.channelName, channelTabFromReturnedJson.channelName)

        val kioskTabFromReturnedJson = requireNonNull<Tab.KioskTab>(Tab.getTabFrom(tabsFromArray[3] as JsonObject) as Tab.KioskTab?)
        assertEquals(kioskTab.tabId.toLong(), kioskTabFromReturnedJson.tabId.toLong())
        assertEquals(kioskTab.kioskServiceId.toLong(), kioskTabFromReturnedJson.kioskServiceId.toLong())
        assertEquals(kioskTab.kioskId, kioskTabFromReturnedJson.kioskId)
    }

    companion object {
        private val JSON_TABS_ARRAY_KEY = "tabs"
        private val JSON_TAB_ID_KEY = "tab_id"
    }
}