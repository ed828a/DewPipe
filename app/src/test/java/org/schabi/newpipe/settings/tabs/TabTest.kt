package org.schabi.newpipe.settings.tabs

import org.junit.Test

import java.util.HashSet

import org.junit.Assert.assertTrue

class TabTest {
    @Test
    fun checkIdDuplication() {
        val usedIds = HashSet<Int>()

        for (type in Tab.Type.values()) {
            val added = usedIds.add(type.tabId)
            assertTrue("Id was already used: " + type.tabId, added)
        }
    }
}