package org.schabi.newpipe.fragments.list.kiosk

import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.ActionBar
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup

import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.KioskTranslator

import icepick.State
import io.reactivex.Single

import org.schabi.newpipe.util.AnimationUtils.animateView

/**
 * Created by Christian Schabesberger on 23.09.17.
 *
 * Copyright (C) Christian Schabesberger 2017 <chris.schabesberger></chris.schabesberger>@mailbox.org>
 * KioskFragment.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

class KioskFragment : BaseListInfoFragment<KioskInfo>() {

    @State
    var kioskId = ""

    lateinit var kioskTranslatedName: String

    ///////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, activity)
        name = kioskTranslatedName
        Log.d(TAG, "KioskFragment::onCreate(), kioskTranslatedName = $kioskTranslatedName")
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (useAsFrontPage && isVisibleToUser && activity != null) {
            try {
                setTitle(kioskTranslatedName)
            } catch (e: Exception) {
                onUnrecoverableError(e, UserAction.UI_ERROR,
                        "none",
                        "none", R.string.app_ui_crash)
            }

        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_kiosk, container, false)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Menu
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        val supportActionBar = activity!!.supportActionBar
        if (supportActionBar != null && useAsFrontPage) {
            supportActionBar.setDisplayHomeAsUpEnabled(false)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Load and handle
    ///////////////////////////////////////////////////////////////////////////

    public override fun loadResult(forceReload: Boolean): Single<KioskInfo> {
        return ExtractorHelper.getKioskInfo(serviceId,
                url,
                forceReload)
    }

    public override fun loadMoreItemsLogic(): Single<ListExtractor.InfoItemsPage<*>> {
        return ExtractorHelper.getMoreKioskItems(serviceId,
                url,
                currentNextPageUrl)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Contract
    ///////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        animateView(itemsList, false, 100)
    }

    override fun handleResult(result: KioskInfo) {
        super.handleResult(result)

        name = kioskTranslatedName
        if (!useAsFrontPage) {
            setTitle(kioskTranslatedName)
        }

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors,
                    UserAction.REQUESTED_KIOSK,
                    NewPipe.getNameOfService(result.serviceId), result.url, 0)
        }
    }

    override fun handleNextItems(result: ListExtractor.InfoItemsPage<*>) {
        super.handleNextItems(result)

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors,
                    UserAction.REQUESTED_PLAYLIST, NewPipe.getNameOfService(serviceId), "Get next page of: $url", 0)
        }
    }

    companion object {

        @Throws(ExtractionException::class)
        @JvmOverloads
        fun getInstance(serviceId: Int,
                        kioskId: String = NewPipe.getService(serviceId)
                                .kioskList
                                .defaultKioskId): KioskFragment {
            Log.d("KioskFragment", "KioskFragment::getInstance() called")
            val instance = KioskFragment()
            val service = NewPipe.getService(serviceId)
            val kioskLinkHandlerFactory = service.kioskList
                    .getListLinkHandlerFactoryByType(kioskId)

            instance.setInitialData(serviceId,
                    kioskLinkHandlerFactory.fromId(kioskId).url, kioskId)

            instance.kioskId = kioskId

            return instance
        }
    }
}
