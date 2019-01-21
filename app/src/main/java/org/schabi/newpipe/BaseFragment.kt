package org.schabi.newpipe

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View

import com.nostra13.universalimageloader.core.ImageLoader
import com.squareup.leakcanary.RefWatcher

import icepick.Icepick
import icepick.State

abstract class BaseFragment : Fragment() {

    protected val TAG = this::class.java.simpleName + "@" + Integer.toHexString(Companion.hashCode())
    protected val DEBUG = MainActivity.DEBUG

    protected var activity: AppCompatActivity? = null

    //These values are used for controlling framgents when they are part of the frontpage
    @State @JvmField
    var useAsFrontPage = false
    protected var mIsVisibleToUser = false

    protected fun getFM(): FragmentManager? =
            if (parentFragment == null)
                fragmentManager
            else
                parentFragment!!.fragmentManager

    fun useAsFrontPage(value: Boolean) {
        useAsFrontPage = value
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        activity = context as AppCompatActivity?
    }

    override fun onDetach() {
        super.onDetach()
        activity = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [$savedInstanceState]")
        super.onCreate(savedInstanceState)
        Icepick.restoreInstanceState(this, savedInstanceState)
        if (savedInstanceState != null) onRestoreInstanceState(savedInstanceState)
    }


    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        if (DEBUG) {
            Log.d(TAG, "onViewCreated() called with: rootView = [$rootView], savedInstanceState = [$savedInstanceState]")
        }
        initViews(rootView, savedInstanceState)
        initListeners()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    protected open fun onRestoreInstanceState(savedInstanceState: Bundle) {}

    override fun onDestroy() {
        super.onDestroy()

        val refWatcher = App.getRefWatcher(getActivity()!!)
        refWatcher?.watch(this)
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        mIsVisibleToUser = isVisibleToUser
    }

    ///////////////////////////////////////////////////////////////////////////
    // Init
    ///////////////////////////////////////////////////////////////////////////

    protected open fun initViews(rootView: View, savedInstanceState: Bundle?) {}

    protected open fun initListeners() {}

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    open fun setTitle(title: String) {
        if (DEBUG) Log.d(TAG, "setTitle() called with: title = [$title]")
        if ((!useAsFrontPage || mIsVisibleToUser) && activity != null && activity!!.supportActionBar != null) {
            activity!!.supportActionBar!!.title = title
        }
    }

    companion object {

        val imageLoader = ImageLoader.getInstance()!!
    }
}
