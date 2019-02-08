package org.schabi.newpipe.report

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.app.NavUtils
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.activity_error.*
import org.acra.ReportField
import org.acra.collector.CrashReportData
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.activity.MainActivity
import org.schabi.newpipe.util.ThemeHelper
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*


class ErrorActivity : AppCompatActivity() {
    private var errorList: Array<String>? = null   // get from intent
    private lateinit var errorInfo: ErrorInfo
    private var returnActivity: Class<*>? = null
    private var currentTimeStamp: String? = null

    private val contentLangString: String?
        get() = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(this.getString(R.string.content_country_key), "none")

    private val osString: String
        get() {
            val osBase = if (Build.VERSION.SDK_INT >= 23) Build.VERSION.BASE_OS else "Android"
            return ("${System.getProperty("os.name")} ${if (osBase.isEmpty()) "Android" else osBase} ${Build.VERSION.RELEASE} - ${Integer.toString(Build.VERSION.SDK_INT)}")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.setTheme(this)
        setContentView(R.layout.activity_error)

        val intent = intent

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        actionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setTitle(R.string.error_report_title)
            it.setDisplayShowTitleEnabled(true)
        }

        val ac = ActivityCommunicator.communicator
        returnActivity = ac.returnActivity
        errorInfo = intent.getParcelableExtra(ERROR_INFO)
        errorList = intent.getStringArrayExtra(ERROR_LIST)

        // important add guru meditation
        addGuruMeditaion()
        currentTimeStamp = getCurrentTimeStamp()

        errorReportButton.setOnClickListener { v: View ->
            val context = this
//            AlertDialog.Builder(context)
//                    .setIcon(android.R.drawable.ic_dialog_alert)
//                    .setTitle(R.string.privacy_policy_title)
//                    .setMessage(R.string.start_accept_privacy_policy)
//                    .setCancelable(false)
//                    .setNeutralButton(R.string.read_privacy_policy) { dialog, which ->
//                        val webIntent = Intent(Intent.ACTION_VIEW,
//                                Uri.parse(context.getString(R.string.privacy_policy_url))
//                        )
//                        context.startActivity(webIntent)
//                    }
//                    .setPositiveButton(R.string.accept) { dialog, which ->
                        val target = Intent(Intent.ACTION_SENDTO)
                        target.setData(Uri.parse("mailto:$ERROR_EMAIL_ADDRESS"))
                                .putExtra(Intent.EXTRA_SUBJECT, ERROR_EMAIL_SUBJECT)
                                .putExtra(Intent.EXTRA_TEXT, buildJson())

                        startActivity(Intent.createChooser(target, "Send Email"))
//                    }
//                    .setNegativeButton(R.string.decline) { dialog, which ->
//                        // do nothing
//                        dialog.dismiss()  // redundant, no this, dialog still dismiss.
//                    }
//                    .show()

        }

        // normal bug report
//        buildInfo(errorInfo)
        if (errorInfo.message != 0) {
            errorMessageView.setText(errorInfo.message)
        } else {
            errorMessageView.visibility = View.GONE
            messageWhatHappenedView.visibility = View.GONE
        }

        errorView.text = formErrorText(errorList)

        //print stack trace once again for debugging:
        for (e in errorList!!) {
            Log.e(TAG, e)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.error_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            android.R.id.home -> goToReturnActivity()
            R.id.menu_item_share_error -> {
                val intent = Intent()
                intent.action = Intent.ACTION_SEND
                intent.putExtra(Intent.EXTRA_TEXT, buildJson())
                intent.type = "text/plain"
                startActivity(Intent.createChooser(intent, getString(R.string.share_dialog_title)))
            }
        }
        return false
    }

    private fun formErrorText(errorList: Array<String>?): String {
        val text = StringBuilder()
        if (errorList != null) {
            for (e in errorList) {
                text.append("-------------------------------------\n").append(e)
            }
        }
        text.append("-------------------------------------")
        return text.toString()
    }

    private fun goToReturnActivity() {
        val checkedReturnActivity = getReturnActivity(returnActivity)
        if (checkedReturnActivity == null) {
            super.onBackPressed()
            // or we can go up to MainActivity with Intent.FLAG_ACTIVITY_CLEAR_TOP flag
//            val intent = Intent(this, MainActivity::class.java)
//            intent.action = Intent.ACTION_MAIN
//            intent.addCategory(Intent.CATEGORY_LAUNCHER)
//            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//            NavUtils.navigateUpTo(this, intent)
        } else {
            val intent = Intent(this, checkedReturnActivity)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            NavUtils.navigateUpTo(this, intent)
        }
    }

//    private fun buildInfo(info: ErrorInfo) {
//        var text = ""
//
//        errorInfoLabelsView.text = getString(R.string.info_labels).replace("\\n", "\n")
//
//        text += (getUserActionString(info.userAction)
//                + "\n" + info.request
//                + "\n" + contentLangString
//                + "\n" + info.serviceName
//                + "\n" + currentTimeStamp
//                + "\n" + packageName
//                + "\n" + BuildConfig.VERSION_NAME
//                + "\n" + osString)
//
//        errorInfosView.text = text
//    }

    private fun buildJson(): String {
        val errorObject = JSONObject()

        try {
            errorObject.put("user_action", getUserActionString(errorInfo.userAction))
                    .put("request", errorInfo.request)
                    .put("content_language", contentLangString)
                    .put("service", errorInfo.serviceName)
                    .put("package", packageName)
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("os", osString)
                    .put("time", currentTimeStamp)

            val exceptionArray = JSONArray()
            if (errorList != null) {
                for (e in errorList!!) {
                    exceptionArray.put(e)
                }
            }

            errorObject.put("exceptions", exceptionArray)
            errorObject.put("user_comment", errorCommentBox!!.text.toString())

            return errorObject.toString(3)
        } catch (e: Throwable) {
            Log.e(TAG, "Error while erroring: Could not build json")
            e.printStackTrace()

            return ""
        }
    }

    private fun getUserActionString(userAction: UserAction?): String =
            userAction?.message ?: "Your description is in another castle."

    private fun addGuruMeditaion() {
        var text = errorSorryView.text.toString()
        text += "\n${getString(R.string.guru_meditation)}"
        errorSorryView.text = text
    }

    override fun onBackPressed() {
        //super.onBackPressed();
        goToReturnActivity()
    }

    private fun getCurrentTimeStamp(): String {
        val df = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        df.timeZone = TimeZone.getTimeZone("GMT")
        return df.format(Date())
    }

    companion object {
        val TAG = ErrorActivity::class.java.toString()
        // BUNDLE TAGS
        const val ERROR_INFO = "error_info"
        const val ERROR_LIST = "error_list"

        const val ERROR_EMAIL_ADDRESS = "ed828a@gmail.com"
        const val ERROR_EMAIL_SUBJECT = "Exception in Aihua " + BuildConfig.VERSION_NAME

        fun reportUiError(activity: AppCompatActivity, el: Throwable) {
            reportError(activity, el, activity.javaClass, null,
                    ErrorInfo.make(UserAction.UI_ERROR, "none", "", R.string.app_ui_crash))
        }

        // will be called at multiple places
        fun reportError(context: Context, el: List<Throwable>?,
                        returnActivity: Class<*>?, rootView: View?, errorInfo: ErrorInfo) {
            if (rootView != null) {
                Snackbar.make(rootView, R.string.error_snackbar_message, 3 * 1000)
                        .setActionTextColor(Color.YELLOW)
                        .setAction(R.string.error_snackbar_action) { v -> startErrorActivity(returnActivity, context, errorInfo, el) }.show()
            } else {
                startErrorActivity(returnActivity, context, errorInfo, el)
            }
        }

        private fun startErrorActivity(returnActivity: Class<*>?, context: Context, errorInfo: ErrorInfo, el: List<Throwable>?) {
            val ac = ActivityCommunicator.communicator
            ac.returnActivity = returnActivity
            val intent = Intent(context, ErrorActivity::class.java)
            intent.putExtra(ERROR_INFO, errorInfo)
            if (el != null)
                intent.putExtra(ERROR_LIST, errorListToStringList(el))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        // will be called at multiple places
        fun reportError(context: Context, e: Throwable?,
                        returnActivity: Class<*>?, rootView: View?, errorInfo: ErrorInfo) {
            var el: MutableList<Throwable>? = null
            if (e != null) {
                el = Vector()
                el.add(e)
            }
            reportError(context, el, returnActivity, rootView, errorInfo)
        }

        // async call, like extractor call it.
        fun reportError(handler: Handler, context: Context, e: Throwable?,
                        returnActivity: Class<*>?, rootView: View?, errorInfo: ErrorInfo) {

            var el: MutableList<Throwable>? = null
            if (e != null) {
                el = Vector()
                el.add(e)
            }
            handler.post { reportError(context, el, returnActivity, rootView, errorInfo) }
        }

        // async call
        fun reportError(handler: Handler, context: Context, el: List<Throwable>?,
                        returnActivity: Class<*>?, rootView: View?, errorInfo: ErrorInfo) {
            handler.post { reportError(context, el, returnActivity, rootView, errorInfo) }
        }

        fun reportError(context: Context, report: CrashReportData, errorInfo: ErrorInfo) {
            // get key first (don't ask about this solution)
            var key: ReportField? = null
            Log.d(TAG, "reportError(): CrashReportData.keys = ${report.keys}")
            for (k in report.keys) {
                if (k.toString() == "STACK_TRACE") {
                    key = k
                }
            }
            val stackTrace = report[key]
            stackTrace?.let {
                val el = arrayOf(it.toString())

                val intent = Intent(context, ErrorActivity::class.java)
                intent.putExtra(ERROR_INFO, errorInfo)
                intent.putExtra(ERROR_LIST, el)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }

        }

        private fun getStackTrace(throwable: Throwable): String {
            val sw = StringWriter()
            val pw = PrintWriter(sw, true)
            throwable.printStackTrace(pw)
            return sw.buffer.toString()
        }

        // errorList to StringList
        private fun errorListToStringList(stackTraces: List<Throwable>): Array<String?> {
            val outProd = arrayOfNulls<String>(stackTraces.size)
            for (index in stackTraces.indices) {
                outProd[index] = getStackTrace(stackTraces[index])
            }
            return outProd
        }

        /**
         * Get the checked activity.
         *
         * @param returnActivity the activity to return to
         * @return the casted return activity or null
         */
        fun getReturnActivity(returnActivity: Class<*>?): Class<out Activity>? {
            var checkedReturnActivity: Class<out Activity>? = null
            if (returnActivity != null) {
                checkedReturnActivity = if (Activity::class.java.isAssignableFrom(returnActivity)) {
                    returnActivity.asSubclass(Activity::class.java)
                } else {
                    MainActivity::class.java
                }
            }
            return checkedReturnActivity
        }
    }
}
