package org.schabi.newpipe.local.subscription

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment

import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper

import icepick.Icepick
import icepick.State

class ImportConfirmationDialog : DialogFragment() {
    @State
    var resultServiceIntent: Intent? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(context, ThemeHelper.getDialogTheme(context))
                .setMessage(R.string.import_network_expensive_warning)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { dialogInterface, i ->
                    if (resultServiceIntent != null && context != null) {
                        context!!.startService(resultServiceIntent)
                    }
                    dismiss()
                }
                .create()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (resultServiceIntent == null) throw IllegalStateException("Result intent is null")

        Icepick.restoreInstanceState(this, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    companion object {

        fun show(fragment: Fragment, resultServiceIntent: Intent) {
            if (fragment.fragmentManager == null) return

            val confirmationDialog = ImportConfirmationDialog()
            confirmationDialog.resultServiceIntent = resultServiceIntent
            confirmationDialog.show(fragment.fragmentManager!!, null)
        }
    }
}
