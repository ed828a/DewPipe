package org.schabi.newpipe.ui.fragments.list

import org.schabi.newpipe.ui.fragments.ViewContract

interface ListViewContract<I, N> : ViewContract<I> {
    fun showListFooter(show: Boolean)

    fun handleNextItems(result: N)
}
