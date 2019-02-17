package org.schabi.newpipe.info_list.cache

import org.schabi.newpipe.info_list.model.Transporter

object TransportCache {
    val transport = Transporter()

    fun reset(){
        with(transport){
            player?.release()
            player = null
            videoSurfaceView = null
            lastPlayingCover = null
        }
    }
}