package org.schabi.newpipe.download.giga.gigaui.fragment

import org.schabi.newpipe.download.giga.get.DownloadManager
import org.schabi.newpipe.download.giga.service.DownloadManagerService

class AllMissionsFragment : MissionsFragment() {

    override fun setupDownloadManager(binder: DownloadManagerService.DMBinder): DownloadManager {
        return binder.downloadManager!!
    }
}
