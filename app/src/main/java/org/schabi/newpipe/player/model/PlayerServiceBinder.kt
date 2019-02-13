package org.schabi.newpipe.player.model

import android.os.Binder
import org.schabi.newpipe.player.BasePlayer

internal class PlayerServiceBinder(val playerInstance: BasePlayer) : Binder()
