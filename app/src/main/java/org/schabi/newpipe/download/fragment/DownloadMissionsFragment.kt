package org.schabi.newpipe.download.fragment

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.Toast
import io.reactivex.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.download.background.DownloadMissionManager
import org.schabi.newpipe.download.adapter.MissionAdapter
import org.schabi.newpipe.download.service.DownloadManagerService
import org.schabi.newpipe.download.background.DeleteDownloadManager

class DownloadMissionsFragment : Fragment() {
    private var mDownloadManager: DownloadMissionManager? = null
    private var mBinder: DownloadManagerService.DMBinder? = null

    private var mPrefs: SharedPreferences? = null
    private var mLinear: Boolean = false
    private var mSwitch: MenuItem? = null

    private var mList: RecyclerView? = null
    private var mAdapter: MissionAdapter? = null
    private var mGridManager: GridLayoutManager? = null
    private var mLinearManager: LinearLayoutManager? = null
    private var mActivity: Context? = null
    private var mDeleteDownloadManager: DeleteDownloadManager? = null
    private var mDeleteDisposable: Disposable? = null

    private val mConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            mBinder = binder as DownloadManagerService.DMBinder
            mDownloadManager = setupDownloadManager(mBinder!!)
            if (mDeleteDownloadManager != null) {
                mDeleteDownloadManager!!.setDownloadManager(mDownloadManager!!)
                updateList()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // What to do?
            mBinder = null
            mDownloadManager = null
            Toast.makeText(this@DownloadMissionsFragment.context,
                    resources.getString(R.string.download_service_disconnected),
                    Toast.LENGTH_SHORT).show()
        }


    }

    fun setDeleteManager(deleteDownloadManager: DeleteDownloadManager) {
        mDeleteDownloadManager = deleteDownloadManager
        if (mDownloadManager != null) {
            mDeleteDownloadManager!!.setDownloadManager(mDownloadManager!!)
            updateList()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.missions, container, false)

        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
        mLinear = mPrefs!!.getBoolean("linear", false)

        // Bind the service
        val intent = Intent()
        val act = activity
        act?.let { activity ->
            intent.setClass(activity, DownloadManagerService::class.java)
            activity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }


        // Views
        mList = view.findViewById(R.id.mission_recycler)

        // Init
        mGridManager = GridLayoutManager(activity, 2)
        mLinearManager = LinearLayoutManager(activity)
        mList?.layoutManager = mGridManager as RecyclerView.LayoutManager?

        setHasOptionsMenu(true)

        return view
    }

    /**
     * Added in API level 23.
     */
    override fun onAttach(activity: Context) {
        super.onAttach(activity)

        // Bug: in api< 23 this is never called
        // so mActivity=null
        // so app crashes with nullpointer exception
        mActivity = activity
    }

    /**
     * deprecated in API level 23,
     * but must remain to allow compatibility with api<23
     */
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)

        mActivity = activity
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (mDeleteDownloadManager != null) {
            mDeleteDisposable = mDeleteDownloadManager!!.undoObservable.subscribe { mission ->
                mAdapter?.let {
                    it.updateItemList()
                    it.notifyDataSetChanged()
                }
//
//                if (mAdapter != null) {
//                    mAdapter!!.updateItemList()
//                    mAdapter!!.notifyDataSetChanged()
//                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.unbindService(mConnection)
        mDeleteDisposable?.dispose()
//        if (mDeleteDisposable != null) {
//            mDeleteDisposable!!.dispose()
//        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        mSwitch = menu.findItem(R.id.switch_mode)
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
            when (item.itemId) {
                R.id.switch_mode -> {
                    mLinear = !mLinear
                    updateList()
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }


    fun notifyChange() {
        mAdapter?.notifyDataSetChanged()
    }

    private fun updateList() {
        mAdapter = MissionAdapter(mActivity as Activity?, mBinder, mDownloadManager, mDeleteDownloadManager, mLinear)

        mList?.layoutManager = if (mLinear) {
            mLinearManager
        } else {
            mGridManager as RecyclerView.LayoutManager?
        }

        mList?.adapter = mAdapter

        if (mSwitch != null) {
            mSwitch!!.setIcon(if (mLinear) R.drawable.grid else R.drawable.list)
        }

        mPrefs!!.edit().putBoolean("linear", mLinear).apply()
    }

    fun setupDownloadManager(binder: DownloadManagerService.DMBinder): DownloadMissionManager =
            binder.downloadManager
}
