package org.schabi.newpipe.local.dialog

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.Window

import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.util.StateSaver
import java.util.Queue

abstract class PlaylistDialog : DialogFragment(), StateSaver.WriteRead {

    protected var streams: List<StreamEntity>? = null  // StateSaver take care this
        private set

    private var savedState: StateSaver.SavedState? = null

    protected fun setInfo(entities: List<StreamEntity>) {
        this.streams = entities
    }

    ///////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedState = StateSaver.tryToRestore(savedInstanceState, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        StateSaver.onDestroy(savedState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        //remove title
        val window = dialog.window
        window?.requestFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    ///////////////////////////////////////////////////////////////////////////
    // State Saving
    ///////////////////////////////////////////////////////////////////////////

    override fun generateSuffix(): String {
        val size = if (streams == null) 0 else streams!!.size
        return ".$size.list"
    }

    override fun writeTo(objectsToSave: Queue<Any>) {
        objectsToSave.add(streams)
    }

    override fun readFrom(savedObjects: Queue<Any>) {
        streams = savedObjects.poll() as List<StreamEntity>
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (activity != null) {
            savedState = StateSaver.tryToSave(activity!!.isChangingConfigurations,
                    savedState, outState, this)
        }
    }
}
