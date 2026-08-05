package com.lujian.travelplan.data

import java.io.File
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlanMediaStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun 大头针编号不能逃逸私有目录且复制内容一致() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val root = temporaryFolder.newFolder("files")
        val store = PlanMediaStore(root, FakeImageSource(bytes))

        val copied = store.copyPhoto(7, "../../危险/pin", "test-image")

        assertTrue(copied.file.canonicalPath.startsWith(root.canonicalPath + File.separator))
        assertFalse(copied.relativePath.contains(".."))
        assertArrayEquals(bytes, copied.file.readBytes())
    }

    @Test
    fun 来源读取失败时不保留临时文件() {
        val root = temporaryFolder.newFolder("failed")
        val store = PlanMediaStore(root, FakeImageSource(byteArrayOf(), fail = true))

        runCatching { store.copyPhoto(9, "pin-1", "test-image") }

        assertTrue(root.walkTopDown().filter(File::isFile).none())
    }

    @Test
    fun 私有路径解析拒绝根目录外文件() {
        val root = temporaryFolder.newFolder("resolve")
        val store = PlanMediaStore(root, FakeImageSource(byteArrayOf()))

        assertNull(store.resolvePrivateFile("../outside.jpg"))
    }

    @Test
    fun 已不存在的私有文件视为删除成功() {
        val root = temporaryFolder.newFolder("idempotent")
        val store = PlanMediaStore(root, FakeImageSource(byteArrayOf()))

        assertTrue(store.deletePrivateFile("plans/1/photos/missing.jpg"))
    }

    @Test
    fun 目录外路径不能被删除() {
        val root = temporaryFolder.newFolder("outside-root")
        val outside = temporaryFolder.newFile("outside.jpg")
        val store = PlanMediaStore(root, FakeImageSource(byteArrayOf()))

        assertFalse(store.deletePrivateFile(outside.canonicalPath))
        assertTrue(outside.exists())
    }
}

private class FakeImageSource(
    private val bytes: ByteArray,
    private val fail: Boolean = false,
) : ImageSource<String> {
    override fun open(reference: String): InputStream = if (fail) error("读取失败") else bytes.inputStream()

    override fun mimeType(reference: String): String = "image/jpeg"

    override fun displayName(reference: String): String = "海边.jpg"
}
