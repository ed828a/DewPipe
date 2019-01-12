package org.schabi.newpipe.download.background

import android.app.Activity
import android.os.Bundle
import android.support.design.widget.BaseTransientBottomBar
import android.support.design.widget.Snackbar
import android.util.Log
import android.view.View
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.schabi.newpipe.R
import java.util.*
import java.util.concurrent.TimeUnit

class DeleteDownloadManager(activity: Activity) {
    // gripe the view of the activity
    private val mView: View = activity.findViewById(android.R.id.content)
    private val mPendingMap: HashSet<String> = HashSet()  // store url
    private val mDisposableList = CompositeDisposable()
    private lateinit var mDownloadMissionManager: DownloadMissionManager
    private val publishSubject = PublishSubject.create<MissionControl>()

    val undoObservable: Observable<MissionControl>
        get() = publishSubject

    operator fun contains(missionControl: MissionControl): Boolean {
        return mPendingMap.contains(missionControl.mission.url)
    }

    fun add(missionControl: MissionControl) {
        mPendingMap.add(missionControl.mission.url)

        if (mPendingMap.size == 1) {
            showUndoDeleteSnackbar(missionControl)
        }
    }

    fun setDownloadManager(downloadManager: DownloadMissionManager) {
        mDownloadMissionManager = downloadManager

        if (mPendingMap.size < 1) return

        showUndoDeleteSnackbar()
    }

    fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return

        val list = savedInstanceState.getStringArrayList(KEY_STATE)
        if (list != null) {
            mPendingMap.addAll(list)
        }
    }

    fun saveState(outState: Bundle?) {
        if (outState == null) return

        mDisposableList.clear()

        outState.putStringArrayList(KEY_STATE, ArrayList(mPendingMap))
    }

    private fun showUndoDeleteSnackbar() {
        if (mPendingMap.size < 1) return

        val url = mPendingMap.iterator().next()

        for (i in 0 until mDownloadMissionManager.count) {
            val missionControl = mDownloadMissionManager.getMission(i)
            if (url == missionControl.mission.url) {
                showUndoDeleteSnackbar(missionControl)
                break
            }
        }
    }

    private fun showUndoDeleteSnackbar(missionControl: MissionControl) {
        Log.d(TAG, "showUndoDeleteSnackbar(MissionControl) on thread: ${Thread.currentThread().name}")
        val snackbar = Snackbar.make(mView, missionControl.mission.name, Snackbar.LENGTH_INDEFINITE)
        val disposable = Observable.timer(3, TimeUnit.SECONDS)
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe { l -> snackbar.dismiss() }

        mDisposableList.add(disposable)

        snackbar.setAction(R.string.undo) { v ->
            mPendingMap.remove(missionControl.mission.url)
            publishSubject.onNext(missionControl)
            disposable.dispose()
            snackbar.dismiss()
        }

        snackbar.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                if (!disposable.isDisposed) {
                    val d = Completable.fromAction { deletePending(missionControl) }
                            .subscribeOn(Schedulers.io())
                            .subscribe()
                    mDisposableList.add(d)
                }
                mPendingMap.remove(missionControl.mission.url)
                snackbar.removeCallback(this)
                mDisposableList.delete(disposable)
                showUndoDeleteSnackbar()
            }
        })

        snackbar.show()
    }

    fun deletePending() {
        if (mPendingMap.size < 1) return

        val idSet = HashSet<Int>()
        for (i in 0 until mDownloadMissionManager.count) {
            if (contains(mDownloadMissionManager.getMission(i))) {
                idSet.add(i)
            }
        }

        for (id in idSet) {
            mDownloadMissionManager.deleteMission(id)
        }

        mPendingMap.clear()
    }

    private fun deletePending(missionControl: MissionControl) {
        for (i in 0 until mDownloadMissionManager.count) {
            if (missionControl.mission.url == mDownloadMissionManager.getMission(i).mission.url) {
                mDownloadMissionManager.deleteMission(i)
                break
            }
        }
    }

    companion object {
        private const val TAG = "DeleteDownloadManager"
        private const val KEY_STATE = "delete_manager_state"
    }
}
