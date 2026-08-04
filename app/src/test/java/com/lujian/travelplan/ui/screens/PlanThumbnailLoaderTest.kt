package com.lujian.travelplan.ui.screens

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlanThumbnailLoaderTest {
    @Test
    fun `缩略图解码离开调用线程`() = runBlocking {
        val callerThread = Thread.currentThread().name
        var decoderThread: String? = null

        PlanThumbnailLoader.decode(File("unused-thumbnail.png")) {
            decoderThread = Thread.currentThread().name
            null
        }

        assertNotNull(decoderThread)
        assertNotEquals(callerThread, decoderThread)
    }
}
