package org.schabi.newpipe.util

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.preference.PreferenceManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import org.schabi.newpipe.App
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.ErrorInfo
import org.schabi.newpipe.report.UserAction
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

/**
 * AsyncTask to check if there is a newer version of the NewPipe github apk available or not.
 * If there is a newer version we show a notification, informing the user. On tapping
 * the notification, the user will be directed to the download link.
 */
class CheckForNewAppVersionTask : AsyncTask<Void, Void, String>() {

    private var mPrefs: SharedPreferences? = null
    private var client: OkHttpClient? = null

    override fun onPreExecute() {

        mPrefs = PreferenceManager.getDefaultSharedPreferences(app)

        // Check if user has enabled/ disabled update checking
        // and if the current apk is a github one or not.
        if (!mPrefs!!.getBoolean(app.getString(R.string.update_app_key), true) || !isGithubApk) {
            this.cancel(true)
        }
    }

    override fun doInBackground(vararg voids: Void): String? {

        // Make a network request to get latest NewPipe data.
        if (client == null) {

            client = OkHttpClient.Builder()
                    .readTimeout(timeoutPeriod.toLong(), TimeUnit.SECONDS)
                    .build()
        }

        val request = Request.Builder()
                .url(newPipeApiUrl)
                .build()

        try {
            val response = client!!.newCall(request).execute()
            return response.body()!!.string()
        } catch (ex: IOException) {
            ErrorActivity.reportError(app, ex, null, null,
                    ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "app update API fail", R.string.app_ui_crash))
        }

        return null
    }

    override fun onPostExecute(response: String?) {

        // Parse the json from the response.
        if (response != null) {

            try {
                val mainObject = JSONObject(response)
                val flavoursObject = mainObject.getJSONObject("flavors")
                val githubObject = flavoursObject.getJSONObject("github")
                val githubStableObject = githubObject.getJSONObject("stable")

                val versionName = githubStableObject.getString("version")
                val versionCode = githubStableObject.getString("version_code")
                val apkLocationUrl = githubStableObject.getString("apk")

                compareAppVersionAndShowNotification(versionName, apkLocationUrl, versionCode)

            } catch (ex: JSONException) {
                ErrorActivity.reportError(app, ex, null, null,
                        ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                                "could not parse app update JSON data", R.string.app_ui_crash))
            }

        }
    }

    /**
     * Method to compare the current and latest available app version.
     * If a newer version is available, we show the update notification.
     * @param versionName
     * @param apkLocationUrl
     */
    private fun compareAppVersionAndShowNotification(versionName: String,
                                                     apkLocationUrl: String,
                                                     versionCode: String) {

        val NOTIFICATION_ID = 2000

        if (BuildConfig.VERSION_CODE < Integer.valueOf(versionCode)) {

            // A pending intent to open the apk location url in the browser.
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkLocationUrl))
            val pendingIntent = PendingIntent.getActivity(app, 0, intent, 0)

            val notificationBuilder = NotificationCompat.Builder(app, app.getString(R.string.app_update_notification_channel_id))
                    .setSmallIcon(R.drawable.ic_newpipe_update)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setContentTitle(app.getString(R.string.app_update_notification_content_title))
                    .setContentText(app.getString(R.string.app_update_notification_content_text)
                            + " " + versionName)

            val notificationManager = NotificationManagerCompat.from(app)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    companion object {

        private val app = App.getApp()
        private val GITHUB_APK_SHA1 = "B0:2E:90:7C:1C:D6:FC:57:C3:35:F0:88:D0:8F:50:5F:94:E4:D2:15"
        private val newPipeApiUrl = "https://newpipe.schabi.org/api/data.json"
        private val timeoutPeriod = 30

        /**
         * Method to get the apk's SHA1 key.
         * https://stackoverflow.com/questions/9293019/get-certificate-fingerprint-from-android-app#22506133
         */
        private val certificateSHA1Fingerprint: String?
            get() {

                val pm = app.packageManager
                val packageName = app.packageName
                val flags = PackageManager.GET_SIGNATURES
                var packageInfo: PackageInfo? = null

                try {
                    packageInfo = pm.getPackageInfo(packageName, flags)
                } catch (ex: PackageManager.NameNotFoundException) {
                    ErrorActivity.reportError(app, ex, null, null,
                            ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                                    "Could not find package info", R.string.app_ui_crash))
                }

                val signatures = packageInfo!!.signatures
                val cert = signatures[0].toByteArray()
                val input = ByteArrayInputStream(cert)

                var cf: CertificateFactory? = null
                var c: X509Certificate? = null

                try {
                    cf = CertificateFactory.getInstance("X509")
                    c = cf!!.generateCertificate(input) as X509Certificate
                } catch (ex: CertificateException) {
                    ErrorActivity.reportError(app, ex, null, null,
                            ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                                    "Certificate error", R.string.app_ui_crash))
                }

                var hexString: String? = null

                try {
                    val md = MessageDigest.getInstance("SHA1")
                    val publicKey = md.digest(c!!.encoded)
                    hexString = byte2HexFormatted(publicKey)
                } catch (ex1: NoSuchAlgorithmException) {
                    ErrorActivity.reportError(app, ex1, null, null,
                            ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                                    "Could not retrieve SHA1 key", R.string.app_ui_crash))
                } catch (ex2: CertificateEncodingException) {
                    ErrorActivity.reportError(app, ex2, null, null,
                            ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                                    "Could not retrieve SHA1 key", R.string.app_ui_crash))
                }

                return hexString
            }

        private fun byte2HexFormatted(arr: ByteArray): String {

            val str = StringBuilder(arr.size * 2)

            for (i in arr.indices) {
                var h = Integer.toHexString(arr[i].toInt())
                val l = h.length
                if (l == 1) h = "0$h"
                if (l > 2) h = h.substring(l - 2, l)
                str.append(h.toUpperCase())
                if (i < arr.size - 1) str.append(':')
            }
            return str.toString()
        }

        val isGithubApk: Boolean
            get() = certificateSHA1Fingerprint == GITHUB_APK_SHA1
    }
}
