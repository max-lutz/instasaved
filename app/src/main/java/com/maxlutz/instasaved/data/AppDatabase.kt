package com.maxlutz.instasaved.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Post::class,
        Collection::class,
        CollectionDeletion::class,
        Tag::class,
        PostTag::class,
        DeletedPostTrace::class,
    ],
    version = 8,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao

    abstract fun collectionDao(): CollectionDao

    abstract fun tagDao(): TagDao

    abstract fun recentlyDeletedDao(): RecentlyDeletedDao

    abstract fun backupDao(): BackupDao

    abstract fun desktopImportDao(): DesktopImportDao

    companion object {
        val MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
        )

        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "instasaved.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

/** Shortcode moves from primary key to a unique column behind a surrogate id; adds url, addedAt, seenInExport. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `posts_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `shortcode` TEXT NOT NULL, " +
                "`url` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `seenInExport` INTEGER NOT NULL)",
        )
        // v1 kept no link or date: rebuild the canonical post link, and date the Post to the migration.
        db.execSQL(
            "INSERT INTO `posts_new` (`shortcode`, `url`, `addedAt`, `seenInExport`) " +
                "SELECT `shortcode`, 'https://www.instagram.com/p/' || `shortcode` || '/', " +
                "CAST(strftime('%s', 'now') AS INTEGER) * 1000, 0 FROM `posts`",
        )
        db.execSQL("DROP TABLE `posts`")
        db.execSQL("ALTER TABLE `posts_new` RENAME TO `posts`")
        db.execSQL("CREATE UNIQUE INDEX `index_posts_shortcode` ON `posts` (`shortcode`)")
    }
}

/** Adds Title, Description, Post Note with their hand-edited flags, and deletedAt for Recently deleted. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "`title` TEXT NOT NULL DEFAULT ''",
            "`titleHandEdited` INTEGER NOT NULL DEFAULT 0",
            "`description` TEXT NOT NULL DEFAULT ''",
            "`descriptionHandEdited` INTEGER NOT NULL DEFAULT 0",
            "`postNote` TEXT NOT NULL DEFAULT ''",
            "`deletedAt` INTEGER",
        ).forEach { db.execSQL("ALTER TABLE `posts` ADD COLUMN $it") }
    }
}

/** Adds Collections, and each Post's Collection plus the Collection deletion it went with. Every Post is in To sort. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL COLLATE NOCASE, `color` INTEGER NOT NULL, `note` TEXT NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX `index_collections_name` ON `collections` (`name`)")
        db.execSQL(
            "CREATE TABLE `collection_deletions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`collectionName` TEXT NOT NULL, `collectionColor` INTEGER NOT NULL, `collectionNote` TEXT NOT NULL)",
        )
        db.execSQL(
            "ALTER TABLE `posts` ADD COLUMN `collectionId` INTEGER " +
                "REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        db.execSQL(
            "ALTER TABLE `posts` ADD COLUMN `deletionId` INTEGER " +
                "REFERENCES `collection_deletions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        db.execSQL("CREATE INDEX `index_posts_collectionId` ON `posts` (`collectionId`)")
        db.execSQL("CREATE INDEX `index_posts_deletionId` ON `posts` (`deletionId`)")
    }
}

/** Adds Tags and the Tags on each Post. No Post has a Tag yet. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL COLLATE NOCASE, `color` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX `index_tags_name` ON `tags` (`name`)")
        db.execSQL(
            "CREATE TABLE `post_tags` (`postId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`postId`, `tagId`), " +
                "FOREIGN KEY(`postId`) REFERENCES `posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX `index_post_tags_tagId` ON `post_tags` (`tagId`)")
    }
}

/** Adds each Post's owner, unknown so far, and its failed Thumbnail downloads: none yet, so every Post is tried. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "`ownerUsername` TEXT NOT NULL DEFAULT ''",
            "`ownerName` TEXT NOT NULL DEFAULT ''",
            "`thumbnailFailures` INTEGER NOT NULL DEFAULT 0",
            "`thumbnailFailedAt` INTEGER",
        ).forEach { db.execSQL("ALTER TABLE `posts` ADD COLUMN $it") }
    }
}

/** Adds the traces of Deleted Posts. None yet: every Deleted Post is still whole, in Recently deleted. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE `deleted_post_traces` (`shortcode` TEXT NOT NULL, PRIMARY KEY(`shortcode`))")
    }
}

/** Adds each Post's modified date. No change was dated so far, so it starts at the date the Post was added. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `posts` ADD COLUMN `modifiedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `posts` SET `modifiedAt` = `addedAt`")
    }
}
