package org.schabi.newpipe.database.history.model

import android.arch.persistence.room.*
import org.schabi.newpipe.database.history.model.SearchHistoryEntry.Companion.SEARCH
import java.util.*

@Entity(tableName = SearchHistoryEntry.TABLE_NAME, indices = [Index(value = [SEARCH])])
class SearchHistoryEntry(@field:ColumnInfo(name = CREATION_DATE)
                         var creationDate: Date?,
                         @field:ColumnInfo(name = SERVICE_ID)
                         var serviceId: Int,
                         @field:ColumnInfo(name = SEARCH)
                         var search: String?) {

    @ColumnInfo(name = ID)
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    fun hasEqualValues(otherEntry: SearchHistoryEntry): Boolean {
        return serviceId == otherEntry.serviceId && search == otherEntry.search
    }

    companion object {

        const val ID = "id"
        const val TABLE_NAME = "search_history"
        const val SERVICE_ID = "service_id"
        const val CREATION_DATE = "creation_date"
        const val SEARCH = "search"
    }
}
