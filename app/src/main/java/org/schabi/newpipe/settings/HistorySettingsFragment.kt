package org.schabi.newpipe.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.ErrorInfo
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.InfoCache

class HistorySettingsFragment : BasePreferenceFragment() {
    private lateinit var cacheWipeKey: String
    private lateinit var viewsHistoryClearKey: String
    private lateinit var searchHistoryClearKey: String
    private var recordManager: HistoryRecordManager? = null
    private var disposables: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cacheWipeKey = getString(R.string.metadata_cache_wipe_key)
        viewsHistoryClearKey = getString(R.string.clear_views_history_key)
        searchHistoryClearKey = getString(R.string.clear_search_history_key)
        activity?.let {
            recordManager = HistoryRecordManager(it)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.history_settings)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {

        when(preference.key){
            cacheWipeKey -> {
                InfoCache.instance.clearCache()
                Toast.makeText(preference.context, R.string.metadata_cache_wipe_complete_notice,
                        Toast.LENGTH_SHORT).show()
            }

            viewsHistoryClearKey -> {
                activity?.let { fragmentActivity ->
                    AlertDialog.Builder(fragmentActivity)
                            .setTitle(R.string.delete_view_history_alert)
                            .setNegativeButton(R.string.cancel) { dialog, which -> dialog.dismiss() }
                            .setPositiveButton(R.string.delete) { dialog, which ->
                                recordManager?.let {historyRecordManager ->
                                    val onDelete = historyRecordManager.deleteWholeStreamHistory()
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(
                                                    { howManyDeleted ->
                                                        Toast.makeText(activity,
                                                                "${resources.getString(R.string.view_history_deleted)} deleted: $howManyDeleted",
                                                                Toast.LENGTH_SHORT).show()
                                                    },
                                                    { throwable ->
                                                        ErrorActivity.reportError(context!!,
                                                                throwable,
                                                                SettingsActivity::class.java,
                                                                null,
                                                                ErrorInfo.make(
                                                                        UserAction.DELETE_FROM_HISTORY,
                                                                        "none",
                                                                        "Delete view history",
                                                                        R.string.general_error))
                                                    })

                                    val onClearOrphans = historyRecordManager.removeOrphanedRecords()
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(
                                                    { howManyDeleted ->
                                                        Toast.makeText(activity, "Deleted $howManyDeleted Orphan records", Toast.LENGTH_SHORT).show()
                                                    },
                                                    { throwable ->
                                                        ErrorActivity.reportError(context!!,
                                                                throwable,
                                                                SettingsActivity::class.java,
                                                                null,
                                                                ErrorInfo.make(
                                                                        UserAction.DELETE_FROM_HISTORY,
                                                                        "none",
                                                                        "Delete search history",
                                                                        R.string.general_error))
                                                    })

                                    disposables.addAll(onClearOrphans, onDelete)
                                }
                            }
                            .create()
                            .show()
                }
            }

            searchHistoryClearKey -> {
                activity?.let { fragmentActivity ->
                    AlertDialog.Builder(fragmentActivity)
                            .setTitle(R.string.delete_search_history_alert)
                            .setNegativeButton(R.string.cancel) { dialog, which -> dialog.dismiss() }
                            .setPositiveButton(R.string.delete) { dialog, which ->
                                recordManager?.let {historyRecordManager ->
                                    val onDelete = historyRecordManager.deleteWholeSearchHistory()
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(
                                                    { howManyDeleted ->
                                                        Toast.makeText(activity,
                                                                R.string.search_history_deleted,
                                                                Toast.LENGTH_SHORT).show()
                                                    },
                                                    { throwable ->
                                                        ErrorActivity.reportError(context!!,
                                                                throwable,
                                                                SettingsActivity::class.java,
                                                                null,
                                                                ErrorInfo.make(
                                                                        UserAction.DELETE_FROM_HISTORY,
                                                                        "none",
                                                                        "Delete search history",
                                                                        R.string.general_error))
                                                    })
                                    disposables.add(onDelete)
                                }
                            }
                            .create()
                            .show()
                }
            }
        }

        return super.onPreferenceTreeClick(preference)
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }
}
