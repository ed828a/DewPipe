package org.schabi.newpipe.database.playlist

import org.schabi.newpipe.database.LocalItem

interface PlaylistLocalItem : LocalItem {
    fun getOrderingName(): String?
}
