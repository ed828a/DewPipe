package org.schabi.newpipe.about

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.AsyncTask
import android.support.v7.app.AlertDialog
import android.webkit.WebView
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper

import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference

class LicenseFragmentHelper(activity: Activity?) : AsyncTask<Any, Void, Int>() {

    internal val weakReference: WeakReference<Activity>
    private var license: License? = null

    private val activity: Activity?
        get() {
            val activity = weakReference.get()

            return if (activity != null && activity.isFinishing) {
                null
            } else {
                activity
            }
        }

    init {
        weakReference = WeakReference<Activity>(activity)
    }

    override fun doInBackground(vararg objects: Any): Int? {
        license = objects[0] as License
        return 1
    }

    override fun onPostExecute(result: Int?) {
        val activity = activity ?: return

        val webViewData = getFormattedLicense(activity, license)
        val alert = AlertDialog.Builder(activity)
        alert.setTitle(license!!.name)

        val wv = WebView(activity)
        wv.loadData(webViewData, "text/html; charset=UTF-8", null)

        alert.setView(wv)
        alert.setNegativeButton(android.R.string.ok) { dialog, which -> dialog.dismiss() }
        alert.show()
    }

    companion object {

        /**
         * @param context the context to use
         * @param license the license
         * @return String which contains a HTML formatted license page styled according to the context's theme
         */
        fun getFormattedLicense(context: Context?, license: License?): String {
            if (context == null) {
                throw NullPointerException("context is null")
            }
            if (license == null) {
                throw NullPointerException("license is null")
            }

            val licenseContent = StringBuilder()
            val webViewData: String
            try {
                val `in` = BufferedReader(InputStreamReader(context.assets.open(license.filename!!), "UTF-8"))
                var str: String? = `in`.readLine()
                while (str != null) {
                    licenseContent.append(str)
                    str = `in`.readLine()
                }
                `in`.close()

                // split the HTML file and insert the stylesheet into the HEAD of the file
                val insert = licenseContent.toString().split("</head>".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                webViewData = (insert[0] + "<style type=\"text/css\">"
                        + getLicenseStylesheet(context) + "</style></head>"
                        + insert[1])
            } catch (e: Exception) {
                throw NullPointerException("could not get license file: ${e.message}, " + getLicenseStylesheet(context))
            }

            return webViewData
        }

        /**
         *
         * @param context
         * @return String which is a CSS stylesheet according to the context's theme
         */
        fun getLicenseStylesheet(context: Context): String {
            val isLightTheme = ThemeHelper.isLightThemeSelected(context)
            return ("body{padding:12px 15px;margin:0;background:#"
                    + getHexRGBColor(context, if (isLightTheme)
                R.color.light_license_background_color
            else
                R.color.dark_license_background_color)
                    + ";color:#"
                    + getHexRGBColor(context, if (isLightTheme)
                R.color.light_license_text_color
            else
                R.color.dark_license_text_color) + ";}"
                    + "a[href]{color:#"
                    + getHexRGBColor(context, if (isLightTheme)
                R.color.light_youtube_primary_color
            else
                R.color.dark_youtube_primary_color) + ";}"
                    + "pre{white-space: pre-wrap;}")
        }

        /**
         * Cast R.color to a hexadecimal color value
         * @param context the context to use
         * @param color the color number from R.color
         * @return a six characters long String with hexadecimal RGB values
         */
        fun getHexRGBColor(context: Context, color: Int): String {
            return context.resources.getString(color).substring(3)
        }
    }

}
