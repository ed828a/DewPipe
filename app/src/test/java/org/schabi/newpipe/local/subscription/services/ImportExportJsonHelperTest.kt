package org.schabi.newpipe.local.subscription.services

import org.junit.Test
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor
import org.schabi.newpipe.extractor.subscription.SubscriptionItem
import org.schabi.newpipe.local.subscription.ImportExportJsonHelper

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Arrays

import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * @see ImportExportJsonHelper
 */
class ImportExportJsonHelperTest {
    @Test
    @Throws(Exception::class)
    fun testEmptySource() {
        val emptySource = "{\"app_version\":\"0.11.6\",\"app_version_int\": 47,\"subscriptions\":[]}"

        val items = ImportExportJsonHelper.readFrom(ByteArrayInputStream(emptySource.toByteArray(charset("UTF-8"))), null)
        assertTrue(items.isEmpty())
    }

    @Test
    fun testInvalidSource() {
        val invalidList = Arrays.asList<String>(
                "{}",
                "", null,
                "gibberish")

        for (invalidContent in invalidList) {
            try {
                if (invalidContent != null) {
                    val bytes = invalidContent.toByteArray(charset("UTF-8"))
                    ImportExportJsonHelper.readFrom(ByteArrayInputStream(bytes), null)
                } else {
                    ImportExportJsonHelper.readFrom(null, null)
                }

                fail("didn't throw exception")
            } catch (e: Exception) {
                val isExpectedException = e is SubscriptionExtractor.InvalidSourceException
                assertTrue("\"" + e.javaClass.simpleName + "\" is not the expected exception", isExpectedException)
            }

        }
    }

    @Test
    @Throws(Exception::class)
    fun ultimateTest() {
        // Read getTabFrom file
        val itemsFromFile = readFromFile()

        // Test writing to an output
        val jsonOut = testWriteTo(itemsFromFile)

        // Read again
        val itemsSecondRead = readFromWriteTo(jsonOut)

        // Check if both lists have the exact same items
        if (itemsFromFile.size != itemsSecondRead.size) {
            fail("The list of items were different getTabFrom each other")
        }

        for (i in itemsFromFile.indices) {
            val item1 = itemsFromFile[i]
            val item2 = itemsSecondRead[i]

            val equals = item1.serviceId == item2.serviceId &&
                    item1.url == item2.url &&
                    item1.name == item2.name

            if (!equals) {
                fail("The list of items were different getTabFrom each other")
            }
        }
    }

    @Throws(Exception::class)
    private fun readFromFile(): List<SubscriptionItem> {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("import_export_test.json")
        val itemsFromFile = ImportExportJsonHelper.readFrom(inputStream, null)

        if (itemsFromFile == null || itemsFromFile.isEmpty()) {
            fail("ImportExportJsonHelper.readFrom(input) returned a null or empty list")
        }

        return itemsFromFile
    }

    @Throws(Exception::class)
    private fun testWriteTo(itemsFromFile: List<SubscriptionItem>): String {
        val out = ByteArrayOutputStream()
        ImportExportJsonHelper.writeTo(itemsFromFile, out, null)
        val jsonOut = out.toString("UTF-8")

        if (jsonOut.isEmpty()) {
            fail("JSON returned by writeTo was empty")
        }

        return jsonOut
    }

    @Throws(Exception::class)
    private fun readFromWriteTo(jsonOut: String): List<SubscriptionItem> {
        val inputStream = ByteArrayInputStream(jsonOut.toByteArray(charset("UTF-8")))
        val secondReadItems = ImportExportJsonHelper.readFrom(inputStream, null)

        if (secondReadItems == null || secondReadItems.isEmpty()) {
            fail("second call to readFrom returned an empty list")
        }

        return secondReadItems
    }
}