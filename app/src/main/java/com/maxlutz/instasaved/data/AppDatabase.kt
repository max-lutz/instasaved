package com.maxlutz.instasaved.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Post::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao

    companion object {
        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

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
