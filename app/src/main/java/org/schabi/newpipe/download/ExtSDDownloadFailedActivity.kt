package org.schabi.newpipe.download

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AppCompatActivity

import org.schabi.newpipe.R
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper

class ExtSDDownloadFailedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this))
    }

    override fun onStart() {
        super.onStart()
        AlertDialog.Builder(this)
                .setTitle(R.string.download_to_sdcard_error_title)
                .setMessage(R.string.download_to_sdcard_error_message)
                .setPositiveButton(R.string.yes) { dialogInterface: DialogInterface, i: Int ->
                    NewPipeSettings.resetDownloadFolders(this)
                    finish()
                }
                .setNegativeButton(R.string.cancel) { dialogInterface: DialogInterface, i: Int ->
                    dialogInterface.dismiss()
                    finish()
                }
                .create()
                .show()
    }
}
