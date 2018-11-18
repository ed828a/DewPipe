package org.schabi.newpipe.settings

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.Preference
import android.widget.Toast

import org.schabi.newpipe.R
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.InfoCache

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

class HistorySettingsFragment : BasePreferenceFragment() {
    private var cacheWipeKey: String? = null
    private var viewsHistroyClearKey: String? = null
    private var searchHistoryClearKey: String? = null
    private var recordManager: HistoryRecordManager? = null
    private var disposables: CompositeDisposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cacheWipeKey = getString(R.string.metadata_cache_wipe_key)
        viewsHistroyClearKey = getString(R.string.clear_views_history_key)
        searchHistoryClearKey = getString(R.string.clear_search_history_key)
        recordManager = HistoryRecordManager(activity)
        disposables = CompositeDisposable()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.history_settings)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == cacheWipeKey) {
            InfoCache.getInstance().clearCache()
            Toast.makeText(preference.context, R.string.metadata_cache_wipe_complete_notice,
                    Toast.LENGTH_SHORT).show()
        }

        if (preference.key == viewsHistroyClearKey) {
            AlertDialog.Builder(activity!!)
                    .setTitle(R.string.delete_view_history_alert)
                    .setNegativeButton(R.string.cancel) { dialog, which -> dialog.dismiss() }
                    .setPositiveButton(R.string.delete) { dialog, which ->
                        val onDelete = recordManager!!.deleteWholeStreamHistory()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        { howManyDeleted ->
                                            Toast.makeText(activity,
                                                    R.string.view_history_deleted,
                                                    Toast.LENGTH_SHORT).show()
                                        },
                                        { throwable ->
                                            ErrorActivity.reportError(context,
                                                    throwable,
                                                    SettingsActivity::class.java, null,
                                                    ErrorActivity.ErrorInfo.make(
                                                            UserAction.DELETE_FROM_HISTORY,
                                                            "none",
                                                            "Delete view history",
                                                            R.string.general_error))
                                        })

                        val onClearOrphans = recordManager!!.removeOrphanedRecords()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        { howManyDeleted -> },
                                        { throwable ->
                                            ErrorActivity.reportError(context,
                                                    throwable,
                                                    SettingsActivity::class.java, null,
                                                    ErrorActivity.ErrorInfo.make(
                                                            UserAction.DELETE_FROM_HISTORY,
                                                            "none",
                                                            "Delete search history",
                                                            R.string.general_error))
                                        })
                        disposables!!.add(onClearOrphans)
                        disposables!!.add(onDelete)
                    }
                    .create()
                    .show()
        }

        if (preference.key == searchHistoryClearKey) {
            AlertDialog.Builder(activity!!)
                    .setTitle(R.string.delete_search_history_alert)
                    .setNegativeButton(R.string.cancel) { dialog, which -> dialog.dismiss() }
                    .setPositiveButton(R.string.delete) { dialog, which ->
                        val onDelete = recordManager!!.deleteWholeSearchHistory()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        { howManyDeleted ->
                                            Toast.makeText(activity,
                                                    R.string.search_history_deleted,
                                                    Toast.LENGTH_SHORT).show()
                                        },
                                        { throwable ->
                                            ErrorActivity.reportError(context,
                                                    throwable,
                                                    SettingsActivity::class.java, null,
                                                    ErrorActivity.ErrorInfo.make(
                                                            UserAction.DELETE_FROM_HISTORY,
                                                            "none",
                                                            "Delete search history",
                                                            R.string.general_error))
                                        })
                        disposables!!.add(onDelete)
                    }
                    .create()
                    .show()
        }

        return super.onPreferenceTreeClick(preference)
    }
}
