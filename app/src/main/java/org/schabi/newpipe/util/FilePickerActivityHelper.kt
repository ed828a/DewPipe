package org.schabi.newpipe.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.support.v4.content.Loader
import android.support.v7.util.SortedList
import android.support.v7.widget.RecyclerView
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast

import com.nononsenseapps.filepicker.AbstractFilePickerFragment
import com.nononsenseapps.filepicker.FilePickerFragment

import org.schabi.newpipe.R

import java.io.File

class FilePickerActivityHelper : com.nononsenseapps.filepicker.FilePickerActivity() {

    private var currentFragment: CustomFilePickerFragment? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        if (ThemeHelper.isLightThemeSelected(this)) {
            this.setTheme(R.style.FilePickerThemeLight)
        } else {
            this.setTheme(R.style.FilePickerThemeDark)
        }
        super.onCreate(savedInstanceState)
    }

    override fun onBackPressed() {
        // If at top most level, normal behaviour
        if (currentFragment!!.isBackTop) {
            super.onBackPressed()
        } else {
            // Else go up
            currentFragment!!.goUp()
        }
    }

    override fun getFragment(startPath: String?,
                             mode: Int,
                             allowMultiple:
                             Boolean,
                             allowCreateDir: Boolean,
                             allowExistingFile: Boolean,
                             singleClick: Boolean): AbstractFilePickerFragment<File> {
        val fragment = CustomFilePickerFragment()
        fragment.setArgs(startPath ?: Environment.getExternalStorageDirectory().path,
                mode, allowMultiple, allowCreateDir, allowExistingFile, singleClick)
        currentFragment = fragment

        return fragment
    }

    ///////////////////////////////////////////////////////////////////////////
    // Internal
    ///////////////////////////////////////////////////////////////////////////

    class CustomFilePickerFragment : FilePickerFragment() {

        val backTop: File
            get() {
                if (arguments == null) return Environment.getExternalStorageDirectory()

                val path = arguments!!.getString(AbstractFilePickerFragment.KEY_START_PATH, "/")
                return if (path.contains(Environment.getExternalStorageDirectory().path)) {
                    Environment.getExternalStorageDirectory()
                } else getPath(path)

            }

        val isBackTop: Boolean
            get() = compareFiles(mCurrentPath, backTop) == 0 || compareFiles(mCurrentPath, File("/")) == 0

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return super.onCreateView(inflater, container, savedInstanceState)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val viewHolder = super.onCreateViewHolder(parent, viewType)

            val view = viewHolder.itemView.findViewById<View>(android.R.id.text1)
            if (view is TextView) {
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.file_picker_items_text_size))
            }

            return viewHolder
        }

        override fun onClickOk(view: View) {
            if (mode == AbstractFilePickerFragment.MODE_NEW_FILE && newFileName.isEmpty()) {
                if (mToast != null) mToast.cancel()
                mToast = Toast.makeText(activity, R.string.file_name_empty_error, Toast.LENGTH_SHORT)
                mToast.show()
                return
            }

            super.onClickOk(view)
        }

        override fun onLoadFinished(loader: Loader<SortedList<File>>, data: SortedList<File>) {
            super.onLoadFinished(loader, data)
            layoutManager.scrollToPosition(0)
        }
    }

    companion object {

        fun chooseSingleFile(context: Context): Intent {
            return Intent(context, FilePickerActivityHelper::class.java)
                    .putExtra(com.nononsenseapps.filepicker.FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(com.nononsenseapps.filepicker.FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false)
                    .putExtra(com.nononsenseapps.filepicker.FilePickerActivity.EXTRA_SINGLE_CLICK, true)
                    .putExtra(com.nononsenseapps.filepicker.FilePickerActivity.EXTRA_MODE,
                            com.nononsenseapps.filepicker.FilePickerActivity.MODE_FILE)
        }

        fun chooseFileToSave(context: Context, startPath: String?): Intent {
            return Intent(context, FilePickerActivityHelper::class.java)
                    .putExtra(com.nononsenseapps.filepicker.FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(com.nononsenseapps.filepicker.FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
                    .putExtra(com.nononsenseapps.filepicker.FilePickerActivity.EXTRA_ALLOW_EXISTING_FILE, true)
                    .putExtra(com.nononsenseapps.filepicker.FilePickerActivity.EXTRA_START_PATH, startPath)
                    .putExtra(com.nononsenseapps.filepicker.FilePickerActivity.EXTRA_MODE,
                            com.nononsenseapps.filepicker.FilePickerActivity.MODE_NEW_FILE)
        }
    }
}
