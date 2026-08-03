package com.lujian.travelplan.importing

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class DecodedHtml(
    val text: String,
    val charsetName: String,
)

object EncodingDetector {
    private val charsetPattern = Regex(
        pattern = """(?i)charset\s*=\s*[\"']?\s*([a-z0-9._-]+)""",
    )

    fun decode(bytes: ByteArray): DecodedHtml {
        detectBom(bytes)?.let { (charset, offset) ->
            return DecodedHtml(
                text = bytes.copyOfRange(offset, bytes.size).toString(charset),
                charsetName = canonicalName(charset),
            )
        }

        val prefix = bytes.copyOfRange(0, minOf(bytes.size, 8 * 1024))
            .toString(StandardCharsets.ISO_8859_1)
        val declaredName = charsetPattern.find(prefix)?.groupValues?.getOrNull(1)
        if (declaredName != null && Charset.isSupported(declaredName)) {
            val charset = Charset.forName(declaredName)
            return DecodedHtml(bytes.toString(charset), canonicalName(charset))
        }

        return try {
            DecodedHtml(decodeStrictUtf8(bytes), "UTF-8")
        } catch (_: CharacterCodingException) {
            val charset = Charset.forName("GB18030")
            DecodedHtml(bytes.toString(charset), "GB18030")
        }
    }

    private fun detectBom(bytes: ByteArray): Pair<Charset, Int>? = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> StandardCharsets.UTF_8 to 3
        bytes.startsWith(0xFF, 0xFE) -> StandardCharsets.UTF_16LE to 2
        bytes.startsWith(0xFE, 0xFF) -> StandardCharsets.UTF_16BE to 2
        else -> null
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String = StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    private fun canonicalName(charset: Charset): String = when (charset.name().uppercase()) {
        "UTF-8" -> "UTF-8"
        "GB18030" -> "GB18030"
        else -> charset.name()
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { index ->
            this[index].toInt() and 0xFF == expected[index]
        }
}
