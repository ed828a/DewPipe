package org.schabi.newpipe.download.background

import android.os.Handler
import java.util.*

/**
 * Created by Edward on 12/21/2018.
 */

interface MissionControlListener {

    fun onProgressUpdate(missionControl: MissionControl, done: Long, total: Long)

    fun onFinish(missionControl: MissionControl)

    fun onError(missionControl: MissionControl, errCode: Int)

    companion object {
        val handlerStore = HashMap<MissionControlListener, Handler>()
    }
}