package com.lujian.travelplan.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class GallerySelectionPolicyTest {
    @Test
    fun `全选去重并在数据刷新后移除失效项`() {
        val photo = GallerySelectionKey.Photo(planId = 1, photoId = 10)
        val cover = GallerySelectionKey.Cover(planId = 1)

        val selected = GallerySelectionPolicy.selectAll(listOf(photo, photo, cover))

        assertEquals(setOf(photo, cover), selected)
        assertEquals(
            setOf(cover),
            GallerySelectionPolicy.retainAvailable(selected, setOf(cover)),
        )
    }

    @Test
    fun `删除摘要分别统计照片和封面`() {
        val summary = GallerySelectionPolicy.summary(
            setOf(
                GallerySelectionKey.Photo(planId = 1, photoId = 10),
                GallerySelectionKey.Cover(planId = 1),
            ),
        )

        assertEquals(1, summary.photos)
        assertEquals(1, summary.covers)
        assertEquals("删除 1 张照片和 1 张自定义预览图？", summary.confirmation)
    }

    @Test
    fun `空选择生成稳定提示且不产生可删除项目`() {
        val summary = GallerySelectionPolicy.summary(emptySet())

        assertEquals(0, summary.photos)
        assertEquals(0, summary.covers)
        assertEquals("没有选择可删除项目", summary.confirmation)
    }
}
