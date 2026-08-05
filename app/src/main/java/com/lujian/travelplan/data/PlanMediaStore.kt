package com.lujian.travelplan.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

interface ImageSource<in T> {
    fun open(reference: T): InputStream
    fun mimeType(reference: T): String?
    fun displayName(reference: T): String?
}

class ContentResolverImageSource(private val resolver: ContentResolver) : ImageSource<Uri> {
    override fun open(reference: Uri): InputStream =
        requireNotNull(resolver.openInputStream(reference)) { "无法读取所选图片" }

    override fun mimeType(reference: Uri): String? = resolver.getType(reference)

    override fun displayName(reference: Uri): String? = resolver.query(
        reference,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.takeIf { index >= 0 && it.moveToFirst() }?.getString(index)
    }
}

data class CopiedPlanImage(
    val file: File,
    val relativePath: String,
    val displayName: String?,
)

class PlanMediaStore<T>(
    filesDir: File,
    private val source: ImageSource<T>,
) {
    private val filesRoot = filesDir.canonicalFile

    fun copyCover(planId: Long, reference: T): CopiedPlanImage =
        copyImage(reference, File(filesRoot, "plans/$planId/cover"))

    fun copyPhoto(planId: Long, pinId: String, reference: T): CopiedPlanImage =
        copyImage(reference, File(filesRoot, "plans/$planId/photos/${pinKey(pinId)}"))

    fun resolvePrivateFile(relativePath: String): File? {
        val candidate = runCatching { File(filesRoot, relativePath).canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.path.startsWith(filesRoot.path + File.separator) }
    }

    fun deletePrivateFile(relativePath: String?): Boolean {
        if (relativePath.isNullOrBlank()) return true
        val file = resolvePrivateFile(relativePath) ?: return false
        return !file.exists() || file.delete()
    }

    fun deletePlanDirectory(planId: Long): Boolean {
        val planRoot = File(filesRoot, "plans/$planId").canonicalFile
        if (!planRoot.path.startsWith(filesRoot.path + File.separator)) return false
        return !planRoot.exists() || planRoot.deleteRecursively()
    }

    private fun copyImage(reference: T, directory: File): CopiedPlanImage {
        val mimeType = source.mimeType(reference)?.lowercase()
        require(mimeType?.startsWith("image/") == true) { "请选择有效图片" }
        val safeDirectory = directory.canonicalFile
        require(safeDirectory.path.startsWith(filesRoot.path + File.separator)) { "图片目录无效" }
        safeDirectory.mkdirs()
        val extension = extensionFor(mimeType)
        val name = UUID.randomUUID().toString()
        val target = File(safeDirectory, "$name.$extension")
        val temporary = File(safeDirectory, ".$name.tmp")
        try {
            source.open(reference).use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
            }
            runCatching {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return CopiedPlanImage(
                file = target,
                relativePath = target.relativeTo(filesRoot).invariantSeparatorsPath,
                displayName = source.displayName(reference),
            )
        } catch (error: Throwable) {
            temporary.delete()
            target.delete()
            throw error
        }
    }

    private fun pinKey(pinId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(pinId.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun extensionFor(mimeType: String): String = when (mimeType) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> "img"
    }
}
