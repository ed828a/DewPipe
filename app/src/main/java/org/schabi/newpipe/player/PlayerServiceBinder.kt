package org.schabi.newpipe.player

import android.os.Binder

internal class PlayerServiceBinder(val playerInstance: BasePlayer) : Binder()
