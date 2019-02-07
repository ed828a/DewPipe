package org.schabi.newpipe.ui.fragments.detail

import java.io.Serializable

internal class StackItem(val serviceId: Int, val url: String, var title: String?) : Serializable {

    override fun toString(): String {
        return "${serviceId.toString()}:$url > $title"
    }
}
