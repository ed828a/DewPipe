package org.schabi.newpipe.player.old

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlinx.android.synthetic.main.activity_play_video.*

import org.schabi.newpipe.R

/*
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * PlayVideoActivity.java is part of NewPipe.
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
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

class PlayVideoActivity : AppCompatActivity() {

    private var videoUrl = ""

    private var actionBar: ActionBar? = null
//    private var videoView: VideoView? = null
    private var position: Int = 0
    private var mediaController: MediaController? = null
//    private var playVideoProgressBar: ProgressBar? = null
    private var decorView: View? = null
    private var uiIsHidden: Boolean = false
    private var isLandscape = true
    private var hasSoftKeys: Boolean = false

    private var prefs: SharedPreferences? = null

    private val navigationBarHeight: Int
        get() {
            if (Build.VERSION.SDK_INT >= 17) {
                val d = windowManager.defaultDisplay

                val realDisplayMetrics = DisplayMetrics()
                d.getRealMetrics(realDisplayMetrics)
                val displayMetrics = DisplayMetrics()
                d.getMetrics(displayMetrics)

                val realHeight = realDisplayMetrics.heightPixels
                val displayHeight = displayMetrics.heightPixels
                return realHeight - displayHeight
            } else {
                return 50
            }
        }

    private val navigationBarWidth: Int
        get() {
            if (Build.VERSION.SDK_INT >= 17) {
                val d = windowManager.defaultDisplay

                val realDisplayMetrics = DisplayMetrics()
                d.getRealMetrics(realDisplayMetrics)
                val displayMetrics = DisplayMetrics()
                d.getMetrics(displayMetrics)

                val realWidth = realDisplayMetrics.widthPixels
                val displayWidth = displayMetrics.widthPixels
                return realWidth - displayWidth
            } else {
                return 50
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_play_video)
        volumeControlStream = AudioManager.STREAM_MUSIC

        //set background arrow style
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white_24dp)

        isLandscape = checkIfLandscape()
        hasSoftKeys = checkIfHasSoftKeys()

        actionBar = supportActionBar
        assert(actionBar != null)
        actionBar!!.setDisplayHomeAsUpEnabled(true)
        val intent = intent
        if (mediaController == null) {
            //prevents back button hiding media controller controls (after showing them)
            //instead of exiting video
            //see http://stackoverflow.com/questions/6051825
            //also solves https://github.com/theScrabi/NewPipe/issues/99
            mediaController = object : MediaController(this) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    val keyCode = event.keyCode
                    val uniqueDown = event.repeatCount == 0 && event.action == KeyEvent.ACTION_DOWN
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        if (uniqueDown) {
                            if (isShowing) {
                                finish()
                            } else {
                                hide()
                            }
                        }
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }
        }

        position = intent.getIntExtra(START_POSITION, 0) * 1000//convert getTabFrom seconds to milliseconds

//        videoView = findViewById(R.id.videoView)
//        playVideoProgressBar = findViewById(R.id.playVideoProgressBar)
        try {
            videoView.setMediaController(mediaController)
            videoView.setVideoURI(Uri.parse(intent.getStringExtra(STREAM_URL)))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        videoView.requestFocus()
        videoView.setOnPreparedListener {
            playVideoProgressBar.visibility = View.GONE
            videoView.seekTo(position)
            if (position <= 0) {
                videoView.start()
                showUi()
            } else {
                videoView.pause()
            }
        }
        videoUrl = intent.getStringExtra(VIDEO_URL)

//        val button = findViewById<Button>(R.id.contentButton)
        contentButton.setOnClickListener {
            if (uiIsHidden) {
                showUi()
            } else {
                hideUi()
            }
        }
        decorView = window.decorView
        decorView!!.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility == View.VISIBLE && uiIsHidden) {
                showUi()
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 17) {
            decorView!!.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }

        prefs = getPreferences(Context.MODE_PRIVATE)
        if (prefs!!.getBoolean(PREF_IS_LANDSCAPE, false) && !isLandscape) {
            toggleOrientation()
        }
    }

    override fun onCreatePanelMenu(featured: Int, menu: Menu): Boolean {
        super.onCreatePanelMenu(featured, menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.video_player, menu)

        return true
    }

    public override fun onPause() {
        super.onPause()
        videoView.pause()
    }

    public override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs = getPreferences(Context.MODE_PRIVATE)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            android.R.id.home -> finish()
            R.id.menu_item_share -> {
                val intent = Intent()
                intent.action = Intent.ACTION_SEND
                intent.putExtra(Intent.EXTRA_TEXT, videoUrl)
                intent.type = "text/plain"
                startActivity(Intent.createChooser(intent, getString(R.string.share_dialog_title)))
            }
            R.id.menu_item_screen_rotation -> toggleOrientation()
            else -> {
                Log.e(TAG, "Error: MenuItem not known")
                return false
            }
        }
        return true
    }

    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)

        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            isLandscape = true
            adjustMediaControlMetrics()
        } else if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            isLandscape = false
            adjustMediaControlMetrics()
        }
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        //savedInstanceState.putInt(POSITION, videoView.getCurrentPosition());
        //videoView.pause();
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        position = savedInstanceState.getInt(POSITION)
        //videoView.seekTo(position);
    }

    private fun showUi() {
        try {
            uiIsHidden = false
            mediaController!!.show(100000)
            actionBar!!.show()
            adjustMediaControlMetrics()
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            val handler = Handler()
            handler.postDelayed({
                if (System.currentTimeMillis() - lastUiShowTime >= HIDING_DELAY) {
                    hideUi()
                }
            }, HIDING_DELAY)
            lastUiShowTime = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun hideUi() {
        uiIsHidden = true
        actionBar!!.hide()
        mediaController!!.hide()
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            decorView!!.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    private fun adjustMediaControlMetrics() {
        val mediaControllerLayout = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT)

        if (!hasSoftKeys) {
            mediaControllerLayout.setMargins(20, 0, 20, 20)
        } else {
            val width = navigationBarWidth
            val height = navigationBarHeight
            mediaControllerLayout.setMargins(width + 20, 0, width + 20, height + 20)
        }
        mediaController!!.layoutParams = mediaControllerLayout
    }

    private fun checkIfHasSoftKeys(): Boolean {
        return Build.VERSION.SDK_INT >= 17 ||
                navigationBarHeight != 0 ||
                navigationBarWidth != 0
    }

    private fun checkIfLandscape(): Boolean {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics.heightPixels < displayMetrics.widthPixels
    }

    private fun toggleOrientation() {
        if (isLandscape) {
            isLandscape = false
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            isLandscape = true
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        val editor = prefs!!.edit()
        editor.putBoolean(PREF_IS_LANDSCAPE, isLandscape)
        editor.apply()
    }

    companion object {

        //// TODO: 11.09.15 add "choose stream" menu

        private val TAG = PlayVideoActivity::class.java.toString()
        val VIDEO_URL = "video_url"
        val STREAM_URL = "stream_url"
        val VIDEO_TITLE = "video_title"
        private val POSITION = "position"
        val START_POSITION = "start_position"

        private val HIDING_DELAY: Long = 3000
        private var lastUiShowTime: Long = 0
        private val PREF_IS_LANDSCAPE = "is_landscape"
    }
}
