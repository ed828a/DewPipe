package org.schabi.newpipe.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.schabi.newpipe.R

object PermissionHelper {
    const val DOWNLOAD_DIALOG_REQUEST_CODE = 778
    const val DOWNLOADS_REQUEST_CODE = 777

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    fun checkStoragePermissions(activity: Activity, requestCode: Int): Boolean {

        if (!checkReadStoragePermissions(activity, requestCode)) return false

        return checkWriteStoragePermissions(activity, requestCode)
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    fun checkReadStoragePermissions(activity: Activity, requestCode: Int): Boolean =
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        requestCode)

                false
            } else
                true


    private fun checkWriteStoragePermissions(activity: Activity, requestCode: Int): Boolean =
            if (ContextCompat.checkSelfPermission(activity,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                // request the permission.
                ActivityCompat.requestPermissions(activity,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        requestCode)

                false
            } else
                true


    /**
     * In order to be able to draw over other apps, the permission android.permission.SYSTEM_ALERT_WINDOW have to be granted.
     *
     *
     * On < API 23 (MarshMallow) the permission was granted when the user installed the application (via AndroidManifest),
     * on > 23, however, it have to start a activity asking the user if he agrees.
     *
     *
     * This method just return if the app has permission to draw over other apps, and if it doesn't, it will try to get the permission.
     *
     * @return returns [Settings.canDrawOverlays]
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    fun checkSystemAlertWindowPermission(context: Context?): Boolean =
            if (!Settings.canDrawOverlays(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context?.packageName}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context?.startActivity(intent)
                false
            } else
                true

    fun isPopupEnabled(context: Context?): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || PermissionHelper.checkSystemAlertWindowPermission(context)


    fun showPopupEnablementToast(context: Context?) {
        Toast.makeText(context, R.string.msg_popup_permission, Toast.LENGTH_LONG)
                .apply {
                    view.findViewById<TextView>(android.R.id.message).gravity = Gravity.CENTER
                }
                .show()
    }
}
