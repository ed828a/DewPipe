package org.schabi.newpipe.download.adapter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.download.background.DeleteDownloadManager
import org.schabi.newpipe.download.background.DownloadMissionManager
import org.schabi.newpipe.download.background.MissionControl
import org.schabi.newpipe.download.background.MissionControl.Companion.NO_ERROR
import org.schabi.newpipe.download.background.MissionControlListener
import org.schabi.newpipe.download.service.DownloadManagerService
import org.schabi.newpipe.download.util.ProgressDrawable
import org.schabi.newpipe.download.util.Utility
import java.io.File
import java.lang.ref.WeakReference
import java.util.*

class MissionAdapter(private val mContext: Activity?,
                     private val mBinder: DownloadManagerService.DMBinder?,
                     private val mDownloadMissionManager: DownloadMissionManager?,
                     private val mDeleteDownloadManager: DeleteDownloadManager?,
                     isLinear: Boolean) : androidx.recyclerview.widget.RecyclerView.Adapter<MissionAdapter.MissionViewHolder>() {

    private val mItemList: MutableList<MissionControl> = ArrayList()
    private val mLayout: Int = if (isLinear) R.layout.mission_item_linear else R.layout.mission_item

    init {
        updateItemList()
    }

    fun updateItemList() {
        mItemList.clear()

        for (i in 0 until mDownloadMissionManager!!.count) {
            val mission = mDownloadMissionManager.getMission(i)
            if (!mDeleteDownloadManager!!.contains(mission)) {
                mItemList.add(mDownloadMissionManager.getMission(i))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MissionAdapter.MissionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(mLayout, parent, false)
        val holder = MissionViewHolder(view)

        holder.menu.setOnClickListener { buildPopup(holder) }

        return holder
    }

    override fun onViewRecycled(viewHolder: MissionAdapter.MissionViewHolder) {
        super.onViewRecycled(viewHolder)
        viewHolder.missionControl!!.removeListener(viewHolder.observer)
        viewHolder.missionControl = null
        viewHolder.observer = null
        viewHolder.progress = null
        viewHolder.itemPosition = -1
        viewHolder.lastTimeStamp = -1
        viewHolder.lastDone = -1
        viewHolder.colorId = 0
    }

    override fun onBindViewHolder(viewHolder: MissionAdapter.MissionViewHolder, pos: Int) {
        val missionControl = mItemList[pos]
        viewHolder.missionControl = missionControl
        viewHolder.itemPosition = pos

        // FileType.MUSIC, VIDEO or UNKNOWN
        val type = Utility.getFileType(missionControl.mission.name)

        viewHolder.icon.setImageResource(Utility.getIconForFileType(type))
        viewHolder.name.text = missionControl.mission.name
        viewHolder.size.text = Utility.formatBytes(missionControl.length)

        viewHolder.progress = ProgressDrawable(mContext!!, Utility.getBackgroundForFileType(type), Utility.getForegroundForFileType(type))
        ViewCompat.setBackground(viewHolder.bkg, viewHolder.progress)

        viewHolder.observer = MissionObserver(this, viewHolder)
        missionControl.addListener(viewHolder.observer!!)

        updateProgress(viewHolder)
    }

    override fun getItemCount(): Int {
        return mItemList.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun updateProgress(holder: MissionViewHolder, finished: Boolean = false) {
        if (holder.missionControl == null) return

        val now = System.currentTimeMillis()

        if (holder.lastTimeStamp == -1L) {
            holder.lastTimeStamp = now
        }

        if (holder.lastDone == -1L) {
            holder.lastDone = holder.missionControl!!.mission.done
        }

        val deltaTime = now - holder.lastTimeStamp
        val deltaDone = holder.missionControl!!.mission.done - holder.lastDone

        if (deltaTime == 0L || deltaTime > 1000 || finished) {
            if (holder.missionControl!!.errCode > 0) {
                holder.status.setText(R.string.msg_error)
            } else {
                val progress = holder.missionControl!!.mission.done.toFloat() / holder.missionControl!!.length

                Log.d(TAG, "Thread.currentThread().name = ${Thread.currentThread().name}, progress = $progress")

                holder.status.text = String.format(Locale.US, "%.2f%%", progress * 100)
                holder.progress!!.setProgress(progress)
            }
        }

        if (deltaTime > 1000 && deltaDone > 0) {
            val speed = deltaDone.toFloat() / deltaTime
            val speedStr = Utility.formatSpeed(speed * 1000)
            val sizeStr = Utility.formatBytes(holder.missionControl!!.length)
            val textString = "$sizeStr $speedStr"
            holder.size.text = textString

            holder.lastTimeStamp = now
            holder.lastDone = holder.missionControl!!.mission.done
        }
    }


    private fun buildPopup(viewHolder: MissionViewHolder) {
        val popup = PopupMenu(mContext, viewHolder.menu)
        popup.inflate(R.menu.mission)

        val menu = popup.menu
        val start = menu.findItem(R.id.start)
        val pause = menu.findItem(R.id.pause)
        val view = menu.findItem(R.id.view)
        val delete = menu.findItem(R.id.delete)
        val checksum = menu.findItem(R.id.checksum)

        // Set to false to hide them first
        start.isVisible = false
        pause.isVisible = false
        view.isVisible = false
        delete.isVisible = false
        checksum.isVisible = false

        if (!viewHolder.missionControl!!.finished) {
            if (!viewHolder.missionControl!!.running) {
                if (viewHolder.missionControl!!.errCode == NO_ERROR) {
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

        popup.setOnMenuItemClickListener { item ->
            val id = item.itemId
            when (id) {
                R.id.start -> {
                    mDownloadMissionManager!!.resumeMission(viewHolder.itemPosition)
                    mBinder!!.onMissionAdded(mItemList[viewHolder.itemPosition])
                    true
                }
                R.id.pause -> {
                    mDownloadMissionManager!!.pauseMission(viewHolder.itemPosition)
                    mBinder!!.onMissionRemoved(mItemList[viewHolder.itemPosition])
                    viewHolder.lastTimeStamp = -1
                    viewHolder.lastDone = -1
                    true
                }
                R.id.view -> {
                    val file = File(viewHolder.missionControl!!.mission.location, viewHolder.missionControl!!.mission.name)
                    val ext = Utility.getFileExt(viewHolder.missionControl!!.mission.name)

                    Log.d(TAG, "Viewing file: ${file.absolutePath} ext: $ext")

                    if (ext == null) {
                        Log.w(TAG, "Can't view file because it has no extension: ${viewHolder.missionControl!!.mission.name}")
                        return@setOnMenuItemClickListener false
                    }

                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.substring(1))
                    Log.v(TAG, "Mime: $mime package: ${mContext!!.applicationContext.packageName}.provider")
                    if (file.exists()) {
                        viewFileWithFileProvider(file, mime)
                    } else {
                        Log.w(TAG, "File doesn't exist")
                    }

                    true
                }
                R.id.delete -> {
                    mDeleteDownloadManager!!.add(viewHolder.missionControl!!)
                    updateItemList()
                    notifyDataSetChanged()
                    true
                }
                R.id.md5, R.id.sha1 -> {
                    val missionControl = mItemList[viewHolder.itemPosition]
                    ChecksumTask(mContext!!).execute(missionControl.mission.location + "/" + missionControl.mission.name, ALGORITHMS[id])
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun viewFileWithFileProvider(file: File, mimetype: String?) {
        val ourPackage = mContext!!.applicationContext.packageName
        val uri = FileProvider.getUriForFile(mContext, "$ourPackage.provider", file)
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
            Toast.makeText(mContext, R.string.toast_no_player, Toast.LENGTH_LONG).show()
        }
    }

    class MissionViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        // because there are 2 type of itemViews, dynamically locating views in itemView is a better choice.
        val status: TextView = view.findViewById(R.id.item_status)
        val icon: ImageView = view.findViewById(R.id.item_icon)
        val name: TextView = view.findViewById(R.id.item_name)
        val size: TextView = view.findViewById(R.id.item_size)
        val bkg: View = view.findViewById(R.id.item_bkg)
        val menu: ImageView = view.findViewById(R.id.item_more)

        var missionControl: MissionControl? = null
        var itemPosition: Int = 0

        var progress: ProgressDrawable? = null
        var observer: MissionObserver? = null

        var lastTimeStamp: Long = -1
        var lastDone: Long = -1
        var colorId: Int = 0

    }

    class MissionObserver(private val mAdapter: MissionAdapter, private val mHolder: MissionViewHolder) : MissionControlListener {
        override fun onProgressUpdate(missionControl: MissionControl, done: Long, total: Long) {
            mAdapter.updateProgress(mHolder)
        }

        override fun onFinish(missionControl: MissionControl) {
            if (mHolder.missionControl != null) {
                mHolder.size.text = Utility.formatBytes(mHolder.missionControl!!.length)
                mAdapter.updateProgress(mHolder, true)
            }
            Log.d(TAG, "missionControl.length: ${missionControl.length}-- mHolder.missionControl!!.length: $mHolder.missionControl!!.length")
        }

        override fun onError(missionControl: MissionControl, errCode: Int) {
            mAdapter.updateProgress(mHolder)
        }

    }

    private class ChecksumTask(activity: Activity) : AsyncTask<String, Void, String>() {
        var progressDialog: AlertDialog? = null

        val weakReference: WeakReference<Activity> = WeakReference(activity)

        private fun getActivity(): Activity? {
            val activity = weakReference.get()

            return if (activity != null && activity.isFinishing) {
                null
            } else {
                activity
            }
        }

        override fun onPreExecute() {
            super.onPreExecute()

            val activity = getActivity()
            if (activity != null) {
                progressDialog = AlertDialog.Builder(activity)
                        .setTitle("Check")
                        .setMessage(activity.getString(R.string.msg_wait))
                        .setPositiveButton("OK"){dialog, which ->
                            Toast.makeText(activity, "Thanks!", Toast.LENGTH_SHORT).show()
                        }
                        .create()
                progressDialog?.show()

            }
        }

        override fun doInBackground(vararg params: String): String {
            return Utility.checksum(params[0], params[1])
        }

        override fun onPostExecute(result: String) {
            super.onPostExecute(result)

            progressDialog?.let {
                Utility.copyToClipboard(it.context, result)
                if (getActivity() != null) {
                    it.dismiss()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MissionAdapter"

        private val ALGORITHMS = hashMapOf(R.id.md5 to "MD5", R.id.sha1 to "SHA1")
    }
}
