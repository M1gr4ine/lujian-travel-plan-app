package com.lujian.travelplan.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lujian.travelplan.data.ImportedPlanFiles
import com.lujian.travelplan.data.PlanRepository
import com.lujian.travelplan.parser.CompositePlanParser
import com.lujian.travelplan.parser.ParseRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DuplicateResolution {
    ASK,
    UPDATE,
    KEEP_COPY,
    CANCEL,
}

sealed interface ImportResult {
    data class Success(
        val planId: Long,
        val locationCandidates: List<LocationCandidate> = emptyList(),
        val unresolvedDestinationName: String? = null,
    ) : ImportResult
    data class Duplicate(val existingPlanId: Long, val existingTitle: String) : ImportResult
    data class Failure(val message: String) : ImportResult
    data object Cancelled : ImportResult
}

class PlanImportService(
    private val context: Context,
    private val repository: PlanRepository,
) {
    private val parser = CompositePlanParser()
    private val locationResolver = LocationResolver(context)

    suspend fun import(
        uri: Uri,
        duplicateResolution: DuplicateResolution = DuplicateResolution.ASK,
    ): ImportResult = withContext(Dispatchers.IO) {
        if (duplicateResolution == DuplicateResolution.CANCEL) return@withContext ImportResult.Cancelled
        runCatching {
            val metadata = queryMetadata(uri)
            val metadataValidation = HtmlFileValidator.validateMetadata(
                metadata.fileName,
                metadata.mimeType,
                metadata.size,
            )
            if (metadataValidation is HtmlValidation.Rejected) {
                return@withContext ImportResult.Failure(metadataValidation.reason)
            }

            val bytes = readLimited(uri)
            val contentValidation = HtmlFileValidator.validate(metadata.fileName, metadata.mimeType, bytes)
            if (contentValidation is HtmlValidation.Rejected) {
                return@withContext ImportResult.Failure(contentValidation.reason)
            }
            val hash = FileHash.sha256(bytes)
            val duplicate = repository.findDuplicate(hash)
            if (duplicate != null && duplicateResolution == DuplicateResolution.ASK) {
                return@withContext ImportResult.Duplicate(duplicate.id, duplicate.title)
            }

            val decoded = EncodingDetector.decode(bytes)
            val parsedSource = parser.parse(ParseRequest(metadata.fileName, metadata.mimeType, decoded.text))
                ?: return@withContext ImportResult.Failure("无法读取这个 HTML 计划")
            val parsed = parsedSource.copy(
                destinations = parsedSource.destinations.map { destination ->
                    if (destination.latitude != null && destination.longitude != null) {
                        destination
                    } else {
                        locationResolver.resolve(destination.name).firstOrNull()?.let { candidate ->
                            destination.copy(
                                countryCode = candidate.countryCode ?: destination.countryCode,
                                latitude = candidate.latitude,
                                longitude = candidate.longitude,
                            )
                        } ?: destination
                    }
                },
            )
            val folder = File(context.filesDir, "plans/import-${UUID.randomUUID()}").apply { mkdirs() }
            val raw = File(folder, "original.html").apply { writeBytes(bytes) }
            val planId = repository.insertImported(
                parsed = parsed,
                files = ImportedPlanFiles(
                    sourceFileName = metadata.fileName,
                    sourceMimeType = metadata.mimeType,
                    charsetName = decoded.charsetName,
                    sha256 = hash,
                    rawPath = raw.relativeTo(context.filesDir).invariantSeparatorsPath,
                ),
                replacePlanId = if (duplicateResolution == DuplicateResolution.UPDATE) duplicate?.id else null,
            )
            enqueueThumbnail(planId, parsed.title)
            val unresolvedDestination = parsed.destinations.firstOrNull {
                it.latitude == null || it.longitude == null
            }
            val candidates = unresolvedDestination?.name
                ?.takeIf { it.isNotBlank() }
                ?.let { locationResolver.resolve(it) }
                .orEmpty()
            ImportResult.Success(planId, candidates, unresolvedDestination?.name)
        }.getOrElse { error ->
            ImportResult.Failure(error.message ?: "读取文件失败")
        }
    }

    private fun readLimited(uri: Uri): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val output = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开文件" }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= HtmlFileValidator.MAX_BYTES) { "文件超过 50 MB" }
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun queryMetadata(uri: Uri): ImportMetadata {
        var fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "旅行计划.html"
        var size: Long? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return ImportMetadata(fileName, context.contentResolver.getType(uri), size)
    }

    private fun enqueueThumbnail(planId: Long, title: String) {
        val input = Data.Builder().putLong("planId", planId).putString("title", title).build()
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ThumbnailWorker>().setInputData(input).build(),
        )
    }

    private data class ImportMetadata(
        val fileName: String,
        val mimeType: String?,
        val size: Long?,
    )
}
