package us.shandian.giga.ui.fragment

import us.shandian.giga.get.DownloadManager
import us.shandian.giga.service.DownloadManagerService

class AllMissionsFragment : MissionsFragment() {

    override fun setupDownloadManager(binder: DownloadManagerService.DMBinder): DownloadManager {
        return binder.downloadManager!!
    }
}
