package org.schabi.newpipe.download.giga.gigaui.adapter

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.support.v4.content.FileProvider
import android.support.v4.view.ViewCompat
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast

import org.schabi.newpipe.R
import org.schabi.newpipe.download.ui.DeleteDownloadManager

import java.io.File
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale

import org.schabi.newpipe.download.giga.get.DownloadManager
import org.schabi.newpipe.download.giga.get.DownloadMission
import org.schabi.newpipe.download.giga.service.DownloadManagerService
import org.schabi.newpipe.download.giga.gigaui.common.ProgressDrawable
import org.schabi.newpipe.download.util.Utility

import android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION

class MissionAdapter(private val mContext: Activity?,
                     private val mBinder: DownloadManagerService.DMBinder?,
                     private val mDownloadManager: DownloadManager?,
                     private val mDeleteDownloadManager: DeleteDownloadManager?,
                     isLinear: Boolean) : RecyclerView.Adapter<MissionAdapter.ViewHolder>() {

    private val mInflater: LayoutInflater = mContext!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val mItemList: MutableList<DownloadMission> = ArrayList()
    private val mLayout: Int = if (isLinear) R.layout.mission_item_linear else R.layout.mission_item

    init {
        updateItemList()
    }

    fun updateItemList() {
        mItemList.clear()

        for (i in 0 until mDownloadManager!!.count) {
            val mission = mDownloadManager.getMission(i)
            if (!mDeleteDownloadManager!!.contains(mission)) {
                mItemList.add(mDownloadManager.getMission(i))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MissionAdapter.ViewHolder {
        val h = ViewHolder(mInflater.inflate(mLayout, parent, false))

        h.menu.setOnClickListener { buildPopup(h) }

        /*h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
			public void onClick(View v) {
				showDetail(h);
			}
		});*/

        return h
    }

    override fun onViewRecycled(h: MissionAdapter.ViewHolder) {
        super.onViewRecycled(h)
        h.mission!!.removeListener(h.observer)
        h.mission = null
        h.observer = null
        h.progress = null
        h.itemPosition = -1
        h.lastTimeStamp = -1
        h.lastDone = -1
        h.colorId = 0
    }

    override fun onBindViewHolder(h: MissionAdapter.ViewHolder, pos: Int) {
        val downloadMission = mItemList[pos]
        h.mission = downloadMission
        h.itemPosition = pos

        val type = Utility.getFileType(downloadMission.name)

        h.icon.setImageResource(Utility.getIconForFileType(type))
        h.name.text = downloadMission.name
        h.size.text = Utility.formatBytes(downloadMission.length)

        h.progress = ProgressDrawable(mContext, Utility.getBackgroundForFileType(type), Utility.getForegroundForFileType(type))
        ViewCompat.setBackground(h.bkg, h.progress)

        h.observer = MissionObserver(this, h)
        downloadMission.addListener(h.observer!!)

        updateProgress(h)
    }

    override fun getItemCount(): Int {
        return mItemList.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun updateProgress(h: ViewHolder, finished: Boolean = false) {
        if (h.mission == null) return

        val now = System.currentTimeMillis()

        if (h.lastTimeStamp == -1L) {
            h.lastTimeStamp = now
        }

        if (h.lastDone == -1L) {
            h.lastDone = h.mission!!.done
        }

        val deltaTime = now - h.lastTimeStamp
        val deltaDone = h.mission!!.done - h.lastDone

        if (deltaTime == 0L || deltaTime > 1000 || finished) {
            if (h.mission!!.errCode > 0) {
                h.status.setText(R.string.msg_error)
            } else {
                val progress = h.mission!!.done.toFloat() / h.mission!!.length
                h.status.text = String.format(Locale.US, "%.2f%%", progress * 100)
                h.progress!!.setProgress(progress)
            }
        }

        if (deltaTime > 1000 && deltaDone > 0) {
            val speed = deltaDone.toFloat() / deltaTime
            val speedStr = Utility.formatSpeed(speed * 1000)
            val sizeStr = Utility.formatBytes(h.mission!!.length)

            h.size.text = "$sizeStr $speedStr"

            h.lastTimeStamp = now
            h.lastDone = h.mission!!.done
        }
    }


    private fun buildPopup(h: ViewHolder) {
        val popup = PopupMenu(mContext, h.menu)
        popup.inflate(R.menu.mission)

        val menu = popup.menu
        val start = menu.findItem(R.id.start)
        val pause = menu.findItem(R.id.pause)
        val view = menu.findItem(R.id.view)
        val delete = menu.findItem(R.id.delete)
        val checksum = menu.findItem(R.id.checksum)

        // Set to false first
        start.isVisible = false
        pause.isVisible = false
        view.isVisible = false
        delete.isVisible = false
        checksum.isVisible = false

        if (!h.mission!!.finished) {
            if (!h.mission!!.running) {
                if (h.mission!!.errCode == -1) {
                    start.isVisible = true
                }

                delete.isVisible = true
            } else {
                pause.isVisible = true
            }
        } else {
            view.isVisible = true
            delete.isVisible = true
            checksum.isVisible = true
        }

        popup.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
            val id = item.itemId
            when (id) {
                R.id.start -> {
                    mDownloadManager!!.resumeMission(h.itemPosition)
                    mBinder!!.onMissionAdded(mItemList[h.itemPosition])
                    true
                }
                R.id.pause -> {
                    mDownloadManager!!.pauseMission(h.itemPosition)
                    mBinder!!.onMissionRemoved(mItemList[h.itemPosition])
                    h.lastTimeStamp = -1
                    h.lastDone = -1
                    true
                }
                R.id.view -> {
                    val f = File(h.mission!!.location, h.mission!!.name)
                    val ext = Utility.getFileExt(h.mission!!.name)

                    Log.d(TAG, "Viewing file: " + f.absolutePath + " ext: " + ext)

                    if (ext == null) {
                        Log.w(TAG, "Can't view file because it has no extension: " + h.mission!!.name)
                        return@OnMenuItemClickListener false
                    }

                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.substring(1))
                    Log.v(TAG, "Mime: " + mime + " package: " + mContext!!.applicationContext.packageName + ".provider")
                    if (f.exists()) {
                        viewFileWithFileProvider(f, mime)
                    } else {
                        Log.w(TAG, "File doesn't exist")
                    }

                    true
                }
                R.id.delete -> {
                    mDeleteDownloadManager!!.add(h.mission!!)
                    updateItemList()
                    notifyDataSetChanged()
                    true
                }
                R.id.md5, R.id.sha1 -> {
                    val mission = mItemList[h.itemPosition]
                    ChecksumTask(mContext!!).execute(mission.location + "/" + mission.name, ALGORITHMS[id])
                    true
                }
                else -> false
            }
        })

        popup.show()
    }

    private fun viewFileWithFileProvider(file: File, mimetype: String?) {
        val ourPackage = mContext!!.applicationContext.packageName
        val uri = FileProvider.getUriForFile(mContext!!, "$ourPackage.provider", file)
        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        intent.setDataAndType(uri, mimetype)
        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.addFlags(FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        //mContext.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Log.v(TAG, "Starting intent: $intent")
        if (intent.resolveActivity(mContext.packageManager) != null) {
            mContext.startActivity(intent)
        } else {
            val noPlayerToast = Toast.makeText(mContext, R.string.toast_no_player, Toast.LENGTH_LONG)
            noPlayerToast.show()
        }
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        var mission: DownloadMission? = null
        var itemPosition: Int = 0

        val status: TextView
        val icon: ImageView
        val name: TextView
        val size: TextView
        val bkg: View
        val menu: ImageView
        var progress: ProgressDrawable? = null
        var observer: MissionObserver? = null

        var lastTimeStamp: Long = -1
        var lastDone: Long = -1
        var colorId: Int = 0

        init {

            status = v.findViewById(R.id.item_status)
            icon = v.findViewById(R.id.item_icon)
            name = v.findViewById(R.id.item_name)
            size = v.findViewById(R.id.item_size)
            bkg = v.findViewById(R.id.item_bkg)
            menu = v.findViewById(R.id.item_more)
        }
    }

    class MissionObserver(private val mAdapter: MissionAdapter, private val mHolder: ViewHolder) : DownloadMission.MissionListener {

        override fun onProgressUpdate(downloadMission: DownloadMission, done: Long, total: Long) {
            mAdapter.updateProgress(mHolder)
        }

        override fun onFinish(downloadMission: DownloadMission) {
            //mAdapter.mManager.deleteMission(mHolder.position);
            // TODO Notification
            //mAdapter.notifyDataSetChanged();
            if (mHolder.mission != null) {
                mHolder.size.text = Utility.formatBytes(mHolder.mission!!.length)
                mAdapter.updateProgress(mHolder, true)
            }
        }

        override fun onError(downloadMission: DownloadMission, errCode: Int) {
            mAdapter.updateProgress(mHolder)
        }

    }

    private class ChecksumTask internal constructor(activity: Activity) : AsyncTask<String, Void, String>() {
        internal var prog: ProgressDialog? = null
        internal val weakReference: WeakReference<Activity>

        private val activity: Activity?
            get() {
                val activity = weakReference.get()

                return if (activity != null && activity.isFinishing) {
                    null
                } else {
                    activity
                }
            }

        init {
            weakReference = WeakReference(activity)
        }

        override fun onPreExecute() {
            super.onPreExecute()

            val activity = activity
            if (activity != null) {
                // Create dialog
                prog = ProgressDialog(activity)
                prog!!.setCancelable(false)
                prog!!.setMessage(activity.getString(R.string.msg_wait))
                prog!!.show()
            }
        }

        override fun doInBackground(vararg params: String): String {
            return Utility.checksum(params[0], params[1])
        }

        override fun onPostExecute(result: String) {
            super.onPostExecute(result)

            if (prog != null) {
                Utility.copyToClipboard(prog!!.context, result)
                if (activity != null) {
                    prog!!.dismiss()
                }
            }
        }
    }

    companion object {
        private val ALGORITHMS = HashMap<Int, String>()
        private const val TAG = "MissionAdapter"

        init {
            ALGORITHMS[R.id.md5] = "MD5"
            ALGORITHMS[R.id.sha1] = "SHA1"
        }
    }
}
