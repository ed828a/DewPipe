package org.schabi.newpipe.player.playqueue.events


class ErrorEvent(val errorIndex: Int, val queueIndex: Int, val isSkippable: Boolean) : PlayQueueEvent {

    override fun type(): PlayQueueEventType {
        return PlayQueueEventType.ERROR
    }
}
