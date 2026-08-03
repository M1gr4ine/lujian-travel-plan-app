package com.lujian.travelplan.ui

object RootTabSwipePolicy {
    fun adjacentIndex(currentIndex: Int, direction: Int, count: Int): Int? {
        if (currentIndex !in 0 until count || direction == 0) return null
        return (currentIndex + direction.sign()).takeIf { it in 0 until count }
    }

    private fun Int.sign(): Int = if (this > 0) 1 else -1
}
