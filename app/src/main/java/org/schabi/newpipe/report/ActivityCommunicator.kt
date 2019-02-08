package org.schabi.newpipe.report


/**
 * Singleton:
 * Used to send data between certain Activity/Services within the same process.
 * This can be considered as an ugly hack inside the Android universe.  */
class ActivityCommunicator {

    @Volatile
    var returnActivity: Class<*>? = null

    companion object {

        private var activityCommunicator: ActivityCommunicator? = null

        val communicator: ActivityCommunicator =
                activityCommunicator ?: synchronized(ActivityCommunicator::class.java){
                    activityCommunicator ?: ActivityCommunicator().also {
                        activityCommunicator = it
                    }
                }

    }
}


