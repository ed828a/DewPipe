package org.schabi.newpipe

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity



class ExitActivity : AppCompatActivity() {

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 21) {
            // Call this when your activity is done and should be closed
            // and the task should be completely removed as a part of
            // finishing the root activity of the task.
            finishAndRemoveTask()
        } else {
            finish()
        }

        System.exit(0)
    }

    companion object {

        fun exitAndRemoveFromRecentApps(activity: Activity) {
            val intent = Intent(activity, ExitActivity::class.java)

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_NO_ANIMATION)

            activity.startActivity(intent)
        }
    }
}
