package com.scarlett.geekradar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TaskEntity::class, LinkEntity::class, PromptSnapshotEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun links(): LinkDao
    abstract fun prompts(): PromptDao

    companion object {
        /** v1 (1.0.0) → v2 (1.1.0)：任务表新增标签字段。只加列，不改动、不删除任何旧数据。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_1_2_SQL[0])
                db.execSQL(MIGRATION_1_2_SQL[1])
            }
        }
        val MIGRATION_1_2_SQL = listOf(
            "ALTER TABLE tasks ADD COLUMN generatedTagsJson TEXT NOT NULL DEFAULT '[]'",
            "ALTER TABLE tasks ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'",
        )

        // 注意：不使用 fallbackToDestructiveMigration，迁移失败时宁可报错也不清空历史。
        fun create(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "geekradar.db").addMigrations(MIGRATION_1_2).build()
    }
}
