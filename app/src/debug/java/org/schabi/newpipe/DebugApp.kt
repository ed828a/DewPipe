package org.schabi.newpipe

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.support.multidex.MultiDex

import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.squareup.leakcanary.AndroidHeapDumper
import com.squareup.leakcanary.DefaultLeakDirectoryProvider
import com.squareup.leakcanary.HeapDumper
import com.squareup.leakcanary.LeakCanary
import com.squareup.leakcanary.LeakDirectoryProvider
import com.squareup.leakcanary.RefWatcher

import org.schabi.newpipe.extractor.Downloader

import java.io.File
import java.util.concurrent.TimeUnit

import okhttp3.OkHttpClient

class DebugApp : App() {

    override val downloader: Downloader
        get() = org.schabi.newpipe.Downloader.init(OkHttpClient.Builder()
                .addNetworkInterceptor(StethoInterceptor()))

    override val isDisposedRxExceptionsReported: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.allow_disposed_exceptions_key), false)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        initStetho()
    }

    private fun initStetho() {
        // Create an InitializerBuilder
        val initializerBuilder = Stetho.newInitializerBuilder(this)

        // Enable Chrome DevTools
        initializerBuilder.enableWebKitInspector(
                Stetho.defaultInspectorModulesProvider(this)
        )

        // Enable command line interface
        initializerBuilder.enableDumpapp(
                Stetho.defaultDumperPluginsProvider(applicationContext)
        )

        // Use the InitializerBuilder to generate an Initializer
        val initializer = initializerBuilder.build()

        // Initialize Stetho with the Initializer
        Stetho.initialize(initializer)
    }

    override fun installLeakCanary(): RefWatcher {
        return LeakCanary.refWatcher(this)
                .heapDumper(ToggleableHeapDumper(this))
                // give each object 10 seconds to be gc'ed, before leak canary gets nosy on it
                .watchDelay(10, TimeUnit.SECONDS)
                .buildAndInstall()
    }

    class ToggleableHeapDumper internal constructor(context: Context) : HeapDumper {
        private val dumper: HeapDumper
        private val preferences: SharedPreferences
        private val dumpingAllowanceKey: String

        private val isDumpingAllowed: Boolean
            get() = preferences.getBoolean(dumpingAllowanceKey, false)

        init {
            val leakDirectoryProvider = DefaultLeakDirectoryProvider(context)
            this.dumper = AndroidHeapDumper(context, leakDirectoryProvider)
            this.preferences = PreferenceManager.getDefaultSharedPreferences(context)
            this.dumpingAllowanceKey = context.getString(R.string.allow_heap_dumping_key)
        }

        override fun dumpHeap(): File {
            return if (isDumpingAllowed) dumper.dumpHeap() else HeapDumper.RETRY_LATER
        }
    }

    companion object {
        private val TAG = DebugApp::class.java.toString()
    }
}
