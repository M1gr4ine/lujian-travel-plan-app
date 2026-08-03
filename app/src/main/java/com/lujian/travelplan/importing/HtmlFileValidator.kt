package com.lujian.travelplan.importing

import com.lujian.travelplan.parser.LujianHtmlContract
import com.lujian.travelplan.parser.LujianHtmlDetection
import java.security.MessageDigest

sealed interface HtmlValidation {
    data object Accepted : HtmlValidation

    data class Rejected(val reason: String) : HtmlValidation
}

object HtmlFileValidator {
    const val MAX_BYTES: Long = 50L * 1024 * 1024

    private val supportedMimeTypes = setOf(
        "text/html",
        "application/xhtml+xml",
        "application/octet-stream",
    )

    fun validateMetadata(
        fileName: String,
        mimeType: String?,
        declaredSize: Long?,
    ): HtmlValidation {
        if (declaredSize != null && declaredSize > MAX_BYTES) {
            return HtmlValidation.Rejected("文件超过 50 MB")
        }
        val hasHtmlExtension = fileName.endsWith(".html", true) || fileName.endsWith(".htm", true)
        val supportedMime = mimeType?.lowercase() in supportedMimeTypes
        return if (hasHtmlExtension || supportedMime) {
            HtmlValidation.Accepted
        } else {
            HtmlValidation.Rejected("请选择 HTML 文件")
        }
    }

    fun validate(
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
    ): HtmlValidation {
        val metadata = validateMetadata(fileName, mimeType, bytes.size.toLong())
        if (metadata is HtmlValidation.Rejected) return metadata
        if (!looksLikeHtml(bytes)) return HtmlValidation.Rejected("文件内容不是有效 HTML")
        return when (val detection = LujianHtmlContract.inspect(EncodingDetector.decode(bytes).text)) {
            LujianHtmlDetection.Absent -> HtmlValidation.Accepted
            is LujianHtmlDetection.Compatible -> HtmlValidation.Accepted
            is LujianHtmlDetection.Incompatible -> HtmlValidation.Rejected(
                "旅笺数据结构无法接入：${detection.reasons.joinToString("；")}",
            )
        }
    }

    private fun looksLikeHtml(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val prefix = EncodingDetector.decode(bytes.copyOfRange(0, minOf(bytes.size, 16 * 1024))).text
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            .lowercase()
        return prefix.startsWith("<!doctype html") ||
            prefix.startsWith("<html") ||
            Regex("""<(head|body|title|meta)(\s|>)""").containsMatchIn(prefix)
    }
}

object FileHash {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
