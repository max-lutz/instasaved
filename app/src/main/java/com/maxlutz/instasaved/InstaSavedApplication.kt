package com.maxlutz.instasaved

import android.app.Application
import com.maxlutz.instasaved.backup.Backups
import com.maxlutz.instasaved.data.AppDatabase
import com.maxlutz.instasaved.deleted.RecentlyDeleted
import com.maxlutz.instasaved.more.Settings
import com.maxlutz.instasaved.sync.Sync
import com.maxlutz.instasaved.sync.SyncStatusStore
import com.maxlutz.instasaved.thumbnails.InstagramThumbnails
import com.maxlutz.instasaved.thumbnails.ThumbnailDownloader
import com.maxlutz.instasaved.thumbnails.ThumbnailRuns
import com.maxlutz.instasaved.thumbnails.ThumbnailStore
import com.maxlutz.instasaved.thumbnails.ThumbnailWorker
import com.maxlutz.instasaved.thumbnails.UrlConnectionHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstaSavedApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.open(this) }

    val thumbnailStore: ThumbnailStore by lazy { ThumbnailStore(this) }

    val recentlyDeleted: RecentlyDeleted by lazy { RecentlyDeleted(database.recentlyDeletedDao(), thumbnailStore) }

    val backups: Backups by lazy { Backups(database.backupDao(), thumbnailStore) }

    val syncStatus: SyncStatusStore by lazy { SyncStatusStore(getSharedPreferences("sync", MODE_PRIVATE)) }

    val settings: Settings by lazy { Settings(getSharedPreferences("settings", MODE_PRIVATE)) }

    val sync: Sync by lazy {
        Sync(database.syncDao(), syncStatus, queueThumbnails = { ThumbnailWorker.downloadNow(this) })
    }

    val thumbnailDownloader: ThumbnailDownloader by lazy {
        val instagram = InstagramThumbnails(UrlConnectionHttp)
        ThumbnailDownloader(
            database.postDao(),
            thumbnailStore,
            fetch = { withContext(Dispatchers.IO) { instagram.fetch(it) } },
            runs = ThumbnailRuns(getSharedPreferences("thumbnail-runs", MODE_PRIVATE)),
        )
    }
}
