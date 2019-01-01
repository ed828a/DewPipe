package org.schabi.newpipe.download.giga.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.support.v4.app.NotificationCompat
import android.support.v4.content.PermissionChecker
import android.util.Log
import android.widget.Toast
import org.schabi.newpipe.BuildConfig
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
    private var mMissionManager: DownloadMissionManager? = null
    private var mNotification: Notification? = null
    private var mHandler: Handler? = null
    private var mLastTimeStamp = System.currentTimeMillis()

    private val missionListener = MissionListener()


    private fun notifyMediaScanner(missionControl: MissionControl) {
        val uri = Uri.parse("file://${missionControl.mission.location}/${missionControl.mission.name}")
        // notify media scanner on downloaded media file ...
        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onCreate")
        }

        mBinder = DMBinder()

        if (mMissionManager == null) {
            val paths = ArrayList<String>(2)
            paths.add(NewPipeSettings.getVideoDownloadPath(this))
            paths.add(NewPipeSettings.getAudioDownloadPath(this))
            mMissionManager = DownloadMissionManagerImpl(paths, this)

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "mMissionManager was == null, curretn mMissionManager = $mMissionManager")
                Log.d(TAG, "Download directory: $paths")
            }
        }

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

        mNotification = builder.build()

        val thread = HandlerThread("ServiceMessenger")
        thread.start()

        mHandler = object : Handler(thread.looper) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    UPDATE_MESSAGE -> {
                        var runningCount = 0

                        for (i in 0 until mMissionManager!!.count) {
                            if (mMissionManager!!.getMission(i).running) {
                                runningCount++
                            }
                        }
                        updateState(runningCount)
                    }
                }
            }
        }

    }

    private fun startMissionAsync(url: String?, location: String, name: String,
                                  isAudio: Boolean, threads: Int) {
        mHandler!!.post {
            val missionId = mMissionManager!!.startMission(url!!, location, name, isAudio, threads)
            mBinder!!.onMissionAdded(mMissionManager!!.getMission(missionId))
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Starting")
        }
        Log.i(TAG, "Got intent: $intent")
        val action = intent.action
        if (action != null && action == Intent.ACTION_RUN) {
            val name = intent.getStringExtra(EXTRA_NAME)
            val location = intent.getStringExtra(EXTRA_LOCATION)
            val threads = intent.getIntExtra(EXTRA_THREADS, 1)
            val isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, false)
            val url = intent.dataString
            startMissionAsync(url, location, name, isAudio, threads)
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Destroying")
        }

        for (i in 0 until mMissionManager!!.count) {
            mMissionManager!!.pauseMission(i)
        }

        (mMissionManager as DownloadMissionManagerImpl).dispose()

        stopForeground(true)

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

    private fun postUpdateMessage() {
        mHandler!!.sendEmptyMessage(UPDATE_MESSAGE)
    }

    private fun updateState(runningCount: Int) {
        if (runningCount == 0) {
            stopForeground(true)
        } else {
            startForeground(NOTIFICATION_ID, mNotification)
        }
    }


    private inner class MissionListener : MissionControlListener {
        override fun onProgressUpdate(missionControl: MissionControl, done: Long, total: Long) {
            val now = System.currentTimeMillis()
            val delta = now - mLastTimeStamp
            if (delta > 2000) {
                postUpdateMessage()
                mLastTimeStamp = now
            }
        }

        override fun onFinish(missionControl: MissionControl) {
            postUpdateMessage()
            notifyMediaScanner(missionControl)
        }

        override fun onError(missionControl: MissionControl, errCode: Int) {
            postUpdateMessage()
        }
    }


    // Wrapper of DownloadMissionManager
    inner class DMBinder : Binder() {
        val downloadManager: DownloadMissionManager?
            get() = mMissionManager

        fun onMissionAdded(missionControl: MissionControl) {
            missionControl.addListener(missionListener)
            postUpdateMessage()
        }

        fun onMissionRemoved(missionControl: MissionControl) {
            missionControl.removeListener(missionListener)
            postUpdateMessage()
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
