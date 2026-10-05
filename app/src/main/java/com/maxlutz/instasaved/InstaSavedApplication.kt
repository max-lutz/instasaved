package com.maxlutz.instasaved

import android.app.Application
import com.maxlutz.instasaved.backup.Backups
import com.maxlutz.instasaved.data.AppDatabase
import com.maxlutz.instasaved.deleted.RecentlyDeleted
import com.maxlutz.instasaved.desktop.DesktopImport
import com.maxlutz.instasaved.thumbnails.InstagramThumbnails
import com.maxlutz.instasaved.thumbnails.ThumbnailDownloader
import com.maxlutz.instasaved.thumbnails.ThumbnailRuns
import com.maxlutz.instasaved.thumbnails.ThumbnailStore
import com.maxlutz.instasaved.thumbnails.UrlConnectionHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstaSavedApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.open(this) }

    val thumbnailStore: ThumbnailStore by lazy { ThumbnailStore(this) }

    val recentlyDeleted: RecentlyDeleted by lazy { RecentlyDeleted(database.recentlyDeletedDao(), thumbnailStore) }

    val backups: Backups by lazy { Backups(database.backupDao(), thumbnailStore) }

    val desktopImport: DesktopImport by lazy { DesktopImport(database.desktopImportDao()) }

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
