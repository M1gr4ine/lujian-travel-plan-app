package com.lujian.travelplan.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lujian.travelplan.data.db.LujianDatabase
import com.lujian.travelplan.data.db.PlanEntity
import com.lujian.travelplan.data.db.PlanPhotoEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GalleryBatchDeletionTest {
    private lateinit var database: LujianDatabase
    private lateinit var filesRoot: File
    private lateinit var repository: PlanRepository

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        filesRoot = File(targetContext.cacheDir, "gallery-batch-delete-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val isolatedContext = object : ContextWrapper(targetContext) {
            override fun getFilesDir(): File = filesRoot
            override fun getApplicationContext(): Context = this
        }
        database = Room.inMemoryDatabaseBuilder(isolatedContext, LujianDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlanRepository(isolatedContext, database)
    }

    @After
    fun tearDown() {
        database.close()
        filesRoot.deleteRecursively()
    }

    @Test
    fun 批量删除照片和封面只影响现存目标() = runBlocking {
        val dao = database.planDao()
        dao.insertPlan(plan(id = 101, title = "大连", coverPath = "plans/101/cover/cover.jpg"))
        dao.insertPlan(plan(id = 102, title = "青岛", coverPath = null))
        dao.insertPhotos(
            listOf(
                photo(id = 201, planId = 101, path = "plans/101/photos/a.jpg"),
                photo(id = 202, planId = 102, path = "plans/102/photos/b.jpg"),
                photo(id = 203, planId = 102, path = "plans/102/photos/keep.jpg"),
            ),
        )
        val deletedFiles = listOf(
            privateFile("plans/101/cover/cover.jpg"),
            privateFile("plans/101/photos/a.jpg"),
            privateFile("plans/102/photos/b.jpg"),
        )
        val keptFile = privateFile("plans/102/photos/keep.jpg")

        val result = repository.removeGalleryItems(
            GalleryDeleteRequest(
                photoIds = setOf(201, 202, 999),
                coverPlanIds = setOf(101, 999),
            ),
        ).getOrThrow()

        assertEquals(2, result.deletedPhotos)
        assertEquals(1, result.deletedCovers)
        assertTrue(dao.findPhotosByIds(setOf(201, 202)).isEmpty())
        assertEquals(listOf(203L), dao.findPhotosByIds(setOf(203)).map { it.id })
        assertNull(dao.findPlanEntitiesByIds(setOf(101)).single().customCoverPath)
        assertTrue(deletedFiles.none(File::exists))
        assertTrue(keptFile.exists())
    }

    @Test
    fun 空批量请求幂等成功() = runBlocking {
        val result = repository.removeGalleryItems(
            GalleryDeleteRequest(emptySet(), emptySet()),
        ).getOrThrow()

        assertEquals(0, result.deletedPhotos)
        assertEquals(0, result.deletedCovers)
        assertFalse(filesRoot.walkTopDown().filter(File::isFile).any())
    }

    private fun privateFile(relativePath: String): File = File(filesRoot, relativePath).apply {
        parentFile?.mkdirs()
        writeBytes(byteArrayOf(1, 2, 3))
    }

    private fun plan(id: Long, title: String, coverPath: String?) = PlanEntity(
        id = id,
        title = title,
        capability = "ENHANCED",
        sourceFileName = "$id.html",
        sourceMimeType = "text/html",
        charsetName = "UTF-8",
        sha256 = "hash-$id",
        rawPath = "plans/$id/raw.html",
        generatedPath = null,
        thumbnailPath = null,
        compatibilityMode = false,
        sectionsJson = "{}",
        createdAt = 1,
        updatedAt = 1,
        customCoverPath = coverPath,
        customCoverAddedAt = coverPath?.let { 10 },
    )

    private fun photo(id: Long, planId: Long, path: String) = PlanPhotoEntity(
        id = id,
        planId = planId,
        pinId = "pin-$id",
        pinTitle = "地点$id",
        relativePath = path,
        addedAt = id,
        displayName = "$id.jpg",
    )
}
