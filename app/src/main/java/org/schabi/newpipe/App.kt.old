package org.schabi.newpipe

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.support.multidex.MultiDexApplication
import android.util.Log
import com.nostra13.universalimageloader.cache.memory.impl.LRULimitedMemoryCache
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration
import com.squareup.leakcanary.LeakCanary
import com.squareup.leakcanary.RefWatcher
import io.reactivex.annotations.NonNull
import io.reactivex.exceptions.CompositeException
import io.reactivex.exceptions.MissingBackpressureException
import io.reactivex.exceptions.OnErrorNotImplementedException
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.functions.Consumer
import io.reactivex.plugins.RxJavaPlugins
import org.acra.ACRA
import org.acra.config.ACRAConfigurationException
import org.acra.config.ConfigurationBuilder
import org.acra.sender.ReportSenderFactory
import org.schabi.newpipe.report.AcraReportSenderFactory
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.ErrorInfo
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.StateSaver
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException

/*
 * Copyright (C) Hans-Christoph Steiner 2016 <hans@eds.org>
 * App.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */


@SuppressLint("Registered")
open class App : MultiDexApplication() {
    private var refWatcher: RefWatcher? = null

    protected open val downloader: Downloader
        get() = org.schabi.newpipe.Downloader.init(
                null)

    protected open val isDisposedRxExceptionsReported: Boolean
        get() = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        initACRA()
    }

    override fun onCreate() {
        super.onCreate()

        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return
        }
        refWatcher = installLeakCanary()

        // Initialize settings first because others inits can use its values
        SettingsActivity.initSettings(this)

        NewPipe.init(downloader,
                org.schabi.newpipe.util.Localization.getPreferredExtractorLocal(this))
        StateSaver.init(this)
        initNotificationChannel()

        // Initialize image loader
        ImageLoader.getInstance().init(getImageLoaderConfigurations(10, 50))

        configureRxJavaErrorHandler()
    }

    private fun configureRxJavaErrorHandler() {
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler(object : Consumer<Throwable> {
            override fun accept(@NonNull throwAble: Throwable) {
                var throwable = throwAble
                Log.e(TAG, "RxJavaPlugins.ErrorHandler called with -> : " +
                        "throwable = [" + throwable.javaClass.name + "]")

                if (throwable is UndeliverableException) {
                    // As UndeliverableException is a wrapper, get the cause of it to get the "real" exception
                    throwable = throwable.cause!!
                }

                val errors: List<Throwable> = if (throwable is CompositeException) {
                    throwable.exceptions
                } else {
                    listOf(throwable)
                }

                for (error in errors) {
                    if (isThrowableIgnored(error)) return
                    if (isThrowableCritical(error)) {
                        reportException(error)
                        return
                    }
                }

                // Out-of-lifecycle exceptions should only be reported if a debug user wishes so,
                // When exception is not reported, log it
                if (isDisposedRxExceptionsReported) {
                    reportException(throwable)
                } else {
                    Log.e(TAG, "RxJavaPlugin: Undeliverable Exception received: ", throwable)
                }
            }

            private fun isThrowableIgnored(@NonNull throwable: Throwable): Boolean {
                // Don't crash the application over a simple network problem
                return ExtractorHelper.hasAssignableCauseThrowable(throwable,
                        IOException::class.java, SocketException::class.java, // network api cancellation
                        InterruptedException::class.java, InterruptedIOException::class.java) // blocking code disposed
            }

            private fun isThrowableCritical(@NonNull throwable: Throwable): Boolean {
                // Though these exceptions cannot be ignored
                return ExtractorHelper.hasAssignableCauseThrowable(throwable,
                        NullPointerException::class.java, IllegalArgumentException::class.java, // bug in app
                        OnErrorNotImplementedException::class.java, MissingBackpressureException::class.java,
                        IllegalStateException::class.java) // bug in operator
            }

            private fun reportException(@NonNull throwable: Throwable) {
                // Throw uncaught exception that will trigger the report system
                Thread.currentThread().uncaughtExceptionHandler
                        .uncaughtException(Thread.currentThread(), throwable)
            }
        })
    }

    private fun getImageLoaderConfigurations(memoryCacheSizeMb: Int,
                                             diskCacheSizeMb: Int): ImageLoaderConfiguration {
        return ImageLoaderConfiguration.Builder(this)
                .memoryCache(LRULimitedMemoryCache(memoryCacheSizeMb * 1024 * 1024))
                .diskCacheSize(diskCacheSizeMb * 1024 * 1024)
                .imageDownloader(ImageDownloader(applicationContext))
                .build()
    }

    private fun initACRA() {
        try {
            val acraConfig = ConfigurationBuilder(this)
                    .setReportSenderFactoryClasses(*reportSenderFactoryClasses as Array<Class<out ReportSenderFactory>>)
                    .setBuildConfigClass(BuildConfig::class.java)
                    .build()
            ACRA.init(this, acraConfig)
        } catch (ace: ACRAConfigurationException) {
            ace.printStackTrace()
            ErrorActivity.reportError(this,
                    ace,
                    null,
                    null,
                    ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Could not initialize ACRA crash report", R.string.app_ui_crash))
        }

    }

    private fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return
        }

        val id = getString(R.string.notification_channel_id)
        val name = getString(R.string.notification_channel_name)
        val description = getString(R.string.notification_channel_description)

        // Keep this below DEFAULT to avoid making noise on every notification update
        val importance = NotificationManager.IMPORTANCE_LOW

        val mChannel = NotificationChannel(id, name, importance)
        mChannel.description = description

        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.createNotificationChannel(mChannel)
    }

    protected open fun installLeakCanary(): RefWatcher {
        return RefWatcher.DISABLED
    }

    companion object {
        protected val TAG = App::class.java.toString()

        private val reportSenderFactoryClasses = arrayOf<Class<*>>(AcraReportSenderFactory::class.java)

        fun getRefWatcher(context: Context): RefWatcher? {
            val application = context.applicationContext as App
            return application.refWatcher
        }
    }
}
