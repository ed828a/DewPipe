package org.schabi.newpipe

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle



class PanicResponderActivity : Activity() {

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        if (intent != null && PANIC_TRIGGER_ACTION == intent.action) {
            // TODO explicitly clear the search results once they are restored when the app restarts
            // or if the app reloads the current video after being killed, that should be cleared also
            ExitActivity.exitAndRemoveFromRecentApps(this)
        }

        if (Build.VERSION.SDK_INT >= 21) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    companion object {

        const val PANIC_TRIGGER_ACTION = "info.guardianproject.panic.action.TRIGGER"
    }
}
