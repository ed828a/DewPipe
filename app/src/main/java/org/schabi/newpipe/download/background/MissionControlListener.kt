package org.schabi.newpipe.download.background

import android.os.Handler
import java.util.*

/**
 * Created by Edward on 12/21/2018.
 */

interface MissionControlListener {
    /**
     * @param done: the progress
     * @param total: the file length
     */
    fun onProgressUpdate(missionControl: MissionControl, done: Long, total: Long)

    fun onFinish(missionControl: MissionControl)

    fun onError(missionControl: MissionControl, errCode: Int)

    companion object {
        // to store Handler associated with UI thread
        val handlerStore = HashMap<MissionControlListener, Handler>()
    }
}