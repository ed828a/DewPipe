package org.schabi.newpipe.download.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.PermissionChecker
import org.schabi.newpipe.R
import org.schabi.newpipe.download.background.DownloadMissionManager
import org.schabi.newpipe.download.background.DownloadMissionManagerImpl
import org.schabi.newpipe.download.background.MissionControl
import org.schabi.newpipe.download.background.MissionControlListener
import org.schabi.newpipe.download.ui.DownloadActivity
import org.schabi.newpipe.settings.NewPipeSettings
import java.util.*

class DownloadManagerService : Service() {
    private var mBinder: DMBinder? = null
    private lateinit var mDownloadManager: DownloadMissionManager
    private var mNotification: Notification? = null
    private lateinit var mHandler: Handler
    private var mLastTimeStamp = System.currentTimeMillis()

    private val missionListener = MissionListener()


    private fun notifyMediaScanner(missionControl: MissionControl) {
        val uri = Uri.parse("file://${missionControl.mission.location}/${missionControl.mission.name}")
        // notify media scanner on downloaded media file ...
        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "DownloadManagerService onCreate() called.")

        mBinder = DMBinder()

        val paths = ArrayList<String>(2)
        paths.add(NewPipeSettings.getVideoDownloadPath(this))
        paths.add(NewPipeSettings.getAudioDownloadPath(this))
        mDownloadManager = DownloadMissionManagerImpl(paths, this)

        Log.d(TAG, "Download directory: $paths")

        val openDownloadListIntent = Intent(this, DownloadActivity::class.java)
                .setAction(Intent.ACTION_MAIN)

        val pendingIntent = PendingIntent.getActivity(this, 0,
                openDownloadListIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        val iconBitmap = BitmapFactory.decodeResource(this.resources, R.mipmap.ic_launcher)

        val builder = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setContentIntent(pendingIntent)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setLargeIcon(iconBitmap)
                .setContentTitle(getString(R.string.msg_running))
                .setContentText(getString(R.string.msg_running_detail))

        builder.setProgress(100, 0, false)

        mNotification = builder.build()

        val thread = HandlerThread("ServiceMessenger")
        thread.start()

        mHandler = Handler(thread.looper) {
            when (it.what) {
                UPDATE_MESSAGE -> {
                    var runningCount = 0

                    for (i in 0 until mDownloadManager.count) {
                        if (mDownloadManager.getMission(i).running) {
                            runningCount++
                        }
                    }
                    val progress = it.arg1
                    Log.d(TAG, "progress = ${it.arg1}, Thread name: ${Thread.currentThread().name}")
                    builder.setProgress(100, progress, false)
                    mNotification = builder.build()
                    updateState(runningCount)
                    true
                }
                else -> false
            }
        }
    }

    private fun startMissionAsync(url: String, location: String, name: String,
                                  isAudio: Boolean, threads: Int) {
        mHandler.post {
            val missionId = mDownloadManager.startMission(url, location, name, isAudio, threads)
            mBinder!!.onMissionAdded(mDownloadManager.getMission(missionId))
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        Log.d(TAG, "DownloadManagerService onStartCommand, service startId: $startId")

        Log.i(TAG, "Got intent: $intent")
        val action = intent.action
        if (action != null && action == Intent.ACTION_RUN) {
            val name = intent.getStringExtra(EXTRA_NAME)
            val location = intent.getStringExtra(EXTRA_LOCATION)
            val threads = intent.getIntExtra(EXTRA_THREADS, 1)
            val isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, false)
            val url = intent.dataString
            if (url == null) {
                Toast.makeText(this, "the provided url isn't available.", Toast.LENGTH_SHORT).show()
            } else {
                startMissionAsync(url, location, name, isAudio, threads)
            }
        }

        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "DownloadManagerService: onDestroy() called, thread name: ${Thread.currentThread().name}")

        for (i in 0 until mDownloadManager.count) {
            mDownloadManager.pauseMission(i)
        }

        stopForeground(true)

        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        var permissionCheck: Int
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            permissionCheck = PermissionChecker.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            if (permissionCheck == PermissionChecker.PERMISSION_DENIED) {
                Toast.makeText(this, "Permission denied (read)", Toast.LENGTH_SHORT).show()
            }
        }

        permissionCheck = PermissionChecker.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissionCheck == PermissionChecker.PERMISSION_DENIED) {
            Toast.makeText(this, "Permission denied (write)", Toast.LENGTH_SHORT).show()
        }

        return mBinder
    }


    /**
     * this function will run on main thread, but notification update is done on ServiceMessenger thread
     */
    private fun postUpdateMessage(progress: Int) {
        val message = Message.obtain()
        message.what = UPDATE_MESSAGE
        message.arg1 = progress
        Log.d(TAG, "postUpdateMessage() progress = $progress on Thread name: ${Thread.currentThread().name}")
        mHandler.sendMessage(message)
    }

    private fun updateState(runningCount: Int) {
        if (runningCount == 0) {
            stopForeground(true)
        } else {
            startForeground(NOTIFICATION_ID, mNotification) // multiple calls of startForeground only update the notification
        }
    }


    private inner class MissionListener : MissionControlListener {
        override fun onProgressUpdate(missionControl: MissionControl, done: Long, total: Long) {
            val now = System.currentTimeMillis()
            val delta = now - mLastTimeStamp
            if (delta > 2000) {
                Log.d(TAG, "done = $done, total = $total")
                val progress = (done * 100 / total).toInt()
                Log.d(TAG, "progress = $progress")
                postUpdateMessage(progress)
                mLastTimeStamp = now
            }
        }

        override fun onFinish(missionControl: MissionControl) {
            Log.d(TAG, "DownloadManagerService: onFinish() called")
            postUpdateMessage(100)
            notifyMediaScanner(missionControl)
        }

        override fun onError(missionControl: MissionControl, errCode: Int) {
            postUpdateMessage(0)
        }
    }


    // Wrapper of DownloadMissionManager
    inner class DMBinder : Binder() {
        val downloadManager: DownloadMissionManager
            get() = mDownloadManager

        fun onMissionAdded(missionControl: MissionControl) {
            Log.d(TAG, "DMBinder onMissionAdded() missionControl.mission.done = ${missionControl.mission.done} /  missionControl.length = ${missionControl.length}")
            missionControl.addListener(missionListener)
            // add or remove missionListener doesn't affect the download progressbar
//            postUpdateMessage(0)  // don't need this line
        }

        fun onMissionRemoved(missionControl: MissionControl) {
            missionControl.removeListener(missionListener)
            // add or remove missionListener doesn't affect the download progressbar
//            postUpdateMessage(0) // don't need this line
        }
    }

    companion object {

        private val TAG = DownloadManagerService::class.java.simpleName

        /**
         * Message code of update messages stored as [Message.what].
         */
        private const val UPDATE_MESSAGE = 0
        private const val NOTIFICATION_ID = 1000
        private const val EXTRA_NAME = "DownloadManagerService.extra.name"
        private const val EXTRA_LOCATION = "DownloadManagerService.extra.location"
        private const val EXTRA_IS_AUDIO = "DownloadManagerService.extra.is_audio"
        private const val EXTRA_THREADS = "DownloadManagerService.extra.threads"

        // utility function for outsiders to call this service.
        fun startMission(context: Context?, url: String, location: String, name: String, isAudio: Boolean, threads: Int) {
            val intent = Intent(context, DownloadManagerService::class.java)
            intent.action = Intent.ACTION_RUN
            intent.data = Uri.parse(url)
            intent.putExtra(EXTRA_NAME, name)
            intent.putExtra(EXTRA_LOCATION, location)
            intent.putExtra(EXTRA_IS_AUDIO, isAudio)
            intent.putExtra(EXTRA_THREADS, threads)
            context?.startService(intent)
        }
    }

}
