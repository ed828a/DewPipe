package org.schabi.newpipe.download.downloadDB

import android.arch.persistence.room.*
import org.schabi.newpipe.download.downloadDB.MissionEntry.Companion.FILE_NAME
import org.schabi.newpipe.download.downloadDB.MissionEntry.Companion.LOCATION
import org.schabi.newpipe.download.downloadDB.MissionEntry.Companion.TABLE_NAME

/**
 * Created by Edward on 12/21/2018.
 */

@Entity(tableName = TABLE_NAME, indices = [Index(value = [LOCATION, FILE_NAME], unique = true)])
data class MissionEntry(
        @field:ColumnInfo(name = FILE_NAME) var name: String = "",    // The filename
        @field:ColumnInfo(name = URL) var url: String = "",           // The url of the file to download
        @field:ColumnInfo(name = LOCATION) var location: String = "", // The directory to store the downloaded
        @field: ColumnInfo(name = DONE) var done: Long = 0,           // Number of bytes downloaded
        @field:ColumnInfo(name = TIMESTAMP) var timestamp: Long = 0
){

    @ColumnInfo(name = ID)
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    fun hasEqualValues(otherEntry: MissionEntry): Boolean {
        return name == otherEntry.name
    }

    companion object {
        const val TABLE_NAME = "download_missions"
        const val ID = "id"
        const val FILE_NAME = "name"
        const val URL = "url"
        const val LOCATION = "location"
        const val DONE = "bytes_downloaded"
        const val TIMESTAMP = "timestamp"
    }
}
