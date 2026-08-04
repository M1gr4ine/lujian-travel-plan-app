package com.lujian.travelplan.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plans ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE plans ADD COLUMN customCoverPath TEXT")
        db.execSQL("ALTER TABLE plans ADD COLUMN customCoverAddedAt INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS plan_photos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "planId INTEGER NOT NULL, " +
                "pinId TEXT NOT NULL, " +
                "pinTitle TEXT NOT NULL, " +
                "relativePath TEXT NOT NULL, " +
                "addedAt INTEGER NOT NULL, " +
                "displayName TEXT, " +
                "FOREIGN KEY(planId) REFERENCES plans(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_photos_planId ON plan_photos(planId)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_plan_photos_planId_pinId " +
                "ON plan_photos(planId, pinId)",
        )
    }
}
