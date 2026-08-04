package com.lujian.travelplan.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LujianDatabaseMigrationTest {
    @Test
    fun 从版本1迁移后保留计划并创建照片表() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(V1_PLAN_TABLE)
                            db.execSQL("CREATE INDEX IF NOT EXISTS index_plans_sha256 ON plans(sha256)")
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        try {
            val database = helper.writableDatabase
            database.execSQL(
                "INSERT INTO plans " +
                    "(id,title,capability,sourceFileName,sourceMimeType,charsetName,sha256,rawPath," +
                    "generatedPath,thumbnailPath,compatibilityMode,sectionsJson,createdAt,updatedAt) " +
                    "VALUES (1,'大连','ENHANCED','dalian.html',NULL,'UTF-8','hash'," +
                    "'plans/1/raw.html',NULL,NULL,0,'{}',10,20)",
            )
            MIGRATION_1_2.migrate(database)

            database.query(
                "SELECT archivedAt, customCoverPath, customCoverAddedAt FROM plans WHERE id=1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
            database.query("SELECT COUNT(*) FROM plan_photos").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            database.query("PRAGMA index_list('plan_photos')").use { cursor ->
                val names = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertTrue("index_plan_photos_planId" in names)
                assertTrue("index_plan_photos_planId_pinId" in names)
            }
        } finally {
            helper.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    private companion object {
        const val TEST_DB = "migration-gallery-test"
        const val V1_PLAN_TABLE =
            "CREATE TABLE IF NOT EXISTS plans (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "title TEXT NOT NULL, capability TEXT NOT NULL, sourceFileName TEXT NOT NULL," +
                "sourceMimeType TEXT, charsetName TEXT NOT NULL, sha256 TEXT NOT NULL," +
                "rawPath TEXT NOT NULL, generatedPath TEXT, thumbnailPath TEXT," +
                "compatibilityMode INTEGER NOT NULL, sectionsJson TEXT NOT NULL," +
                "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
    }
}
