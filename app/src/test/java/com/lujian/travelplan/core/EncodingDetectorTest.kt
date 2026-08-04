package com.lujian.travelplan.core

import com.lujian.travelplan.importing.EncodingDetector
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class EncodingDetectorTest {
    @Test
    fun `严格 UTF-8 文件按 UTF-8 解码`() {
        val bytes = "<meta charset=\"UTF-8\"><title>大连旅行</title>".toByteArray(Charsets.UTF_8)

        val result = EncodingDetector.decode(bytes)

        assertEquals("UTF-8", result.charsetName)
        assertEquals(true, result.text.contains("大连旅行"))
    }

    @Test
    fun `meta 声明 GB18030 时按声明编码解码`() {
        val charset = Charset.forName("GB18030")
        val bytes = "<meta charset=\"GB18030\"><title>青岛计划</title>".toByteArray(charset)

        val result = EncodingDetector.decode(bytes)

        assertEquals("GB18030", result.charsetName)
        assertEquals(true, result.text.contains("青岛计划"))
    }
}
