package org.schabi.newpipe.ui.fragments.list.kiosk

import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import icepick.State
import io.reactivex.Single
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.ui.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.util.AnimationUtils.animateView
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.KioskTranslator


class KioskFragment : BaseListInfoFragment<KioskInfo>() {

    @State @JvmField
    protected var kioskId = ""
    protected lateinit var kioskTranslatedName: String

    ///////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, activity!!)
        name = kioskTranslatedName
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
        val contentCountry = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getString(getString(R.string.content_country_key),
                        getString(R.string.default_country_value))

        Log.d(TAG, "loadResult(forceReload=$forceReload), serviceId = $serviceId, url = $url")
        // temporary solution to fix resume NO_SERVICE_ID bug
//        if (serviceId == NO_SERVICE_ID) {
//            serviceId = 0
//            url = "https://www.youtube.com/feed/trending"
//        }
        return ExtractorHelper.getKioskInfo(serviceId,
                url,
                forceReload)
    }

    public override fun loadMoreItemsLogic(): Single<ListExtractor.InfoItemsPage<*>> {
        val contentCountry = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getString(getString(R.string.content_country_key),
                        getString(R.string.default_country_value))
        return ExtractorHelper.getMoreKioskItems(serviceId,
                url,
                currentNextPageUrl)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Contract
    ///////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        animateView(itemsList!!, false, 100)
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
        fun getInstance(serviceId: Int, kioskId: String = NewPipe.getService(serviceId)
                .kioskList
                .defaultKioskId): KioskFragment {
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
}///////////////////////////////////////////////////////////////////////////
// Views
///////////////////////////////////////////////////////////////////////////
