package org.schabi.newpipe.fragments.list

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View

import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.util.Constants

import java.util.Queue

import icepick.State
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

abstract class BaseListInfoFragment<I : ListInfo<*>> : BaseListFragment<I, ListExtractor.InfoItemsPage<*>>() {

    @State
    var serviceId = Constants.NO_SERVICE_ID
    @State
    var name: String = ""
    @State
    var url: String = ""

    protected var currentInfo: I? = null
    protected var currentNextPageUrl: String = ""
    protected var currentWorker: Disposable? = null

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        setTitle(name)
        showListFooter(hasMoreItems())
    }

    override fun onPause() {
        super.onPause()
        if (currentWorker != null) currentWorker!!.dispose()
    }

    override fun onResume() {
        super.onResume()
        // Check if it was loading when the fragment was stopped/paused,
        if (wasLoading.getAndSet(false)) {
            if (hasMoreItems() && infoListAdapter!!.itemsList.size > 0) {
                loadMoreItems()
            } else {
                doInitialLoadLogic()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentWorker != null) currentWorker!!.dispose()
        currentWorker = null
    }

    ///////////////////////////////////////////////////////////////////////////
    // State Saving
    ///////////////////////////////////////////////////////////////////////////

    override fun writeTo(objectsToSave: Queue<Any>) {
        super.writeTo(objectsToSave)
        objectsToSave.add(currentInfo)
        objectsToSave.add(currentNextPageUrl)
    }

    @Throws(Exception::class)
    override fun readFrom(savedObjects: Queue<Any>) {
        super.readFrom(savedObjects)
        currentInfo = savedObjects.poll() as I?
        currentNextPageUrl = savedObjects.poll() as String
    }

    ///////////////////////////////////////////////////////////////////////////
    // Load and handle
    ///////////////////////////////////////////////////////////////////////////

    override fun doInitialLoadLogic() {
        if (DEBUG) Log.d(TAG, "doInitialLoadLogic() called, serviceId = $serviceId")
        if (currentInfo == null) {
            startLoading(false)
        } else
            handleResult(currentInfo!!)
    }

    /**
     * Implement the logic to load the info getTabFrom the network.<br></br>
     * You can use the default implementations getTabFrom [org.schabi.newpipe.util.ExtractorHelper].
     *
     * @param forceLoad allow or disallow the result to come getTabFrom the cache
     */
    protected abstract fun loadResult(forceLoad: Boolean): Single<I>

    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)

        showListFooter(false)
        currentInfo = null
        if (currentWorker != null) currentWorker!!.dispose()
        currentWorker = loadResult(forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result: I ->
                    isLoading.set(false)
                    currentInfo = result
                    currentNextPageUrl = result.nextPageUrl
                    handleResult(result)
                }, { throwable: Throwable -> onError(throwable) })
    }

    /**
     * Implement the logic to load more items<br></br>
     * You can use the default implementations getTabFrom [org.schabi.newpipe.util.ExtractorHelper]
     */
    protected abstract fun loadMoreItemsLogic(): Single<ListExtractor.InfoItemsPage<*>>

    override fun loadMoreItems() {
        isLoading.set(true)

        if (currentWorker != null) currentWorker!!.dispose()
        currentWorker = loadMoreItemsLogic()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ InfoItemsPage: ListExtractor.InfoItemsPage<*> ->
                    isLoading.set(false)
                    handleNextItems(InfoItemsPage)
                }, { throwable: Throwable ->
                    isLoading.set(false)
                    onError(throwable)
                })
    }

    override fun handleNextItems(result: ListExtractor.InfoItemsPage<*>) {
        super.handleNextItems(result)
        currentNextPageUrl = result.nextPageUrl
        infoListAdapter!!.addInfoItemList(result.items)

        showListFooter(hasMoreItems())
    }

    override fun hasMoreItems(): Boolean {
        return !TextUtils.isEmpty(currentNextPageUrl)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Contract
    ///////////////////////////////////////////////////////////////////////////

    override fun handleResult(result: I) {
        super.handleResult(result)

        name = result.name
        setTitle(result.name)

        if (infoListAdapter!!.itemsList.size == 0) {
            if (result.relatedItems.size > 0) {
                infoListAdapter!!.addInfoItemList(result.relatedItems)
                showListFooter(hasMoreItems())
            } else {
                infoListAdapter!!.clearStreamItemList()
                showEmptyState()
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    protected fun setInitialData(serviceId: Int, url: String, name: String) {
        if (DEBUG) Log.d(TAG, "setInitialData() called, serviceId = $serviceId")
        this.serviceId = serviceId
        this.url = url
        this.name = if (!TextUtils.isEmpty(name)) name else ""
    }
}
