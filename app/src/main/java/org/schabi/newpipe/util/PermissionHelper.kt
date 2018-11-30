package org.schabi.newpipe.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

import org.schabi.newpipe.R

object PermissionHelper {
    val DOWNLOAD_DIALOG_REQUEST_CODE = 778
    val DOWNLOADS_REQUEST_CODE = 777


    fun checkStoragePermissions(activity: Activity, requestCode: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (!checkReadStoragePermissions(activity, requestCode)) return false
        }
        return checkWriteStoragePermissions(activity, requestCode)
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    fun checkReadStoragePermissions(activity: Activity, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    requestCode)

            return false
        }
        return true
    }


    fun checkWriteStoragePermissions(activity: Activity, requestCode: Int): Boolean {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            /*if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {*/

            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    requestCode)

            // PERMISSION_WRITE_STORAGE is an
            // app-defined int constant. The callback method gets the
            // result of the request.
            /*}*/
            return false
        }
        return true
    }


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
    fun checkSystemAlertWindowPermission(context: Context?): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context?.packageName))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context?.startActivity(i)
            return false
        } else
            return true
    }

    fun isPopupEnabled(context: Context?): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || PermissionHelper.checkSystemAlertWindowPermission(context)
    }

    fun showPopupEnablementToast(context: Context?) {
        val toast = Toast.makeText(context, R.string.msg_popup_permission, Toast.LENGTH_LONG)
        val messageView = toast.view.findViewById<TextView>(android.R.id.message)
        if (messageView != null) messageView.gravity = Gravity.CENTER
        toast.show()
    }
}
