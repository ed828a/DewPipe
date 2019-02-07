package org.schabi.newpipe.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import org.schabi.newpipe.R

class BlankFragment : BaseFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setTitle(TITLE)
        return inflater.inflate(R.layout.fragment_blank, container, false)
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        setTitle(TITLE)

    }
    companion object {
        const val TITLE = "DEW"
    }
}
