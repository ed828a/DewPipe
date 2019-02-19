package org.schabi.newpipe.util

import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.ServiceList.SoundCloud
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import java.util.concurrent.TimeUnit

// NewPipe Extractor support only 2 service so far: YouTube and SoundCloud.
object ServiceHelper {
    private const val TAG = "ServiceHelper"
    private val DEFAULT_FALLBACK_SERVICE = ServiceList.YouTube
    private const val YOUTUBE_SERVICE_ID = 0
    private const val SOUNDCLOUD_SERVICE_ID = 1

    @DrawableRes
    fun getIcon(serviceId: Int): Int {
        return when (serviceId) {
            YOUTUBE_SERVICE_ID -> R.drawable.place_holder_youtube
            SOUNDCLOUD_SERVICE_ID -> R.drawable.place_holder_circle
            else -> R.drawable.service
        }
    }

    fun getTranslatedFilterString(filter: String, c: Context): String {
        return when (filter) {
            "all" -> c.getString(R.string.all)
            "videos" -> c.getString(R.string.videos)
            "channels" -> c.getString(R.string.channels)
            "playlists" -> c.getString(R.string.playlists)
            "tracks" -> c.getString(R.string.tracks)
            "users" -> c.getString(R.string.users)
            else -> filter
        }
    }

    /**
     * Get a resource string with instructions for importing subscriptions for each service.
     *
     * @return the string resource containing the instructions or -1 if the service don't support it
     */
    @StringRes
    fun getImportInstructions(serviceId: Int): Int {
        return when (serviceId) {
            YOUTUBE_SERVICE_ID -> R.string.import_youtube_instructions
            SOUNDCLOUD_SERVICE_ID -> R.string.import_soundcloud_instructions
            else -> -1
        }
    }

    /**
     * For services that support importing getTabFrom a channel url, return a hint that will
     * be used in the EditText that the user will type in his channel url.
     *
     * @return the hint's string resource or -1 if the service don't support it
     */
    @StringRes
    fun getImportInstructionsHint(serviceId: Int): Int {
        return when (serviceId) {
            SOUNDCLOUD_SERVICE_ID -> R.string.import_soundcloud_instructions_hint
            else -> -1
        }
    }

    fun getSelectedServiceId(context: Context): Int {

        val serviceName = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.current_service_key), context.getString(R.string.default_service_value))

        val serviceId: Int = try {
            NewPipe.getService(serviceName).serviceId
        } catch (e: ExtractionException) {
            DEFAULT_FALLBACK_SERVICE.serviceId
        }

        Log.d(TAG, "getSelectedServiceId(): serviceName: $serviceName, serviceId = $serviceId")
        return serviceId
    }

    fun setSelectedServiceId(context: Context, serviceId: Int) {
        val serviceName: String = try {
            NewPipe.getService(serviceId).serviceInfo.name
        } catch (e: ExtractionException) {
            DEFAULT_FALLBACK_SERVICE.serviceInfo.name
        }

        setSelectedServicePreferences(context, serviceName)
    }

    fun setSelectedServiceId(context: Context, serviceName: String) {
        var serviceName = serviceName
        val serviceId = NewPipe.getIdOfService(serviceName)
        if (serviceId == -1) serviceName = DEFAULT_FALLBACK_SERVICE.serviceInfo.name

        setSelectedServicePreferences(context, serviceName)
    }

    private fun setSelectedServicePreferences(context: Context, serviceName: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(context.getString(R.string.current_service_key), serviceName)
                .apply()
    }

    fun getCacheExpirationMillis(serviceId: Int): Long {
        return if (serviceId == SoundCloud.serviceId) {
            TimeUnit.MILLISECONDS.convert(15, TimeUnit.MINUTES)
        } else {
            TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS)
        }
    }

    fun isBeta(streamingService: StreamingService): Boolean {
        return when (streamingService.serviceInfo.name) {
            "YouTube" -> false
            else -> true
        }
    }
}
