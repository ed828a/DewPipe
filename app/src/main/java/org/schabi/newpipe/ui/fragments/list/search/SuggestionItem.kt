package org.schabi.newpipe.ui.fragments.list.search

class SuggestionItem(val fromHistory: Boolean, val query: String?) {

    override fun toString(): String {
        return "[$fromHistory→$query]"
    }
}
