package org.schabi.newpipe.util

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Created by Christian Schabesberger on 28.01.18.
 * Copyright 2018 Christian Schabesberger <chris.schabesberger></chris.schabesberger>@mailbox.org>
 * ZipHelper.java is part of NewPipe
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
 * along with this program. If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

object ZipHelper {

    private const val BUFFER_SIZE = 2048

    /**
     * This function helps to create zip files.
     * Caution this will override the original file.
     * @param outZip The ZipOutputStream where the data should be stored in
     * @param file The path of the file that should be added to zip.
     * @param name The path of the file inside the zip.
     * @throws Exception
     */
    @Throws(Exception::class)
    fun addFileToZip(outZip: ZipOutputStream, file: String, name: String) {
        val data = ByteArray(BUFFER_SIZE)
        val fi = FileInputStream(file)
        val inputStream = BufferedInputStream(fi, BUFFER_SIZE)
        val entry = ZipEntry(name)
        outZip.putNextEntry(entry)
        var count: Int = inputStream.read(data, 0, BUFFER_SIZE)
        while (count != -1) {
            outZip.write(data, 0, count)
            count = inputStream.read(data, 0, BUFFER_SIZE)
        }
        inputStream.close()
    }

    /**
     * This will extract data from Zipfiles.
     * Caution this will override the original file.
     * @param file The path of the file on the disk where the data should be extracted to.
     * @param name The path of the file inside the zip.
     * @return will return true if the file was found within the zip file
     * @throws Exception
     */
    @Throws(Exception::class)
    fun extractFileFromZip(filePath: String, file: String, name: String): Boolean {

        val inZip = ZipInputStream(
                BufferedInputStream(
                        FileInputStream(filePath)))

        val data = ByteArray(BUFFER_SIZE)

        var found = false

        val ze: ZipEntry? = inZip.nextEntry
        while (ze != null) {
            if (ze.name == name) {
                found = true
                // delete old file first
                val oldFile = File(file)
                if (oldFile.exists()) {
                    if (!oldFile.delete()) {
                        throw Exception("Could not delete $file")
                    }
                }

                val outFile = FileOutputStream(file)
                var count = inZip.read(data)
                while (count != -1) {
                    outFile.write(data, 0, count)
                    count = inZip.read(data)
                }

                outFile.close()
                inZip.closeEntry()
            }
        }
        return found
    }
}
