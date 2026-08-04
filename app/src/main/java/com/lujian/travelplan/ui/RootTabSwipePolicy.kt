package com.lujian.travelplan.ui

object RootTabSwipePolicy {
    fun directionForGesture(
        totalX: Float,
        totalY: Float,
        threshold: Float,
        childConsumed: Boolean,
        allowConsumedGesture: Boolean = false,
    ): Int? {
        if ((childConsumed && !allowConsumedGesture) || kotlin.math.abs(totalX) < threshold) return null
        if (kotlin.math.abs(totalX) <= kotlin.math.abs(totalY) * 1.25f) return null
        return if (totalX < 0f) 1 else -1
    }

    fun adjacentIndex(currentIndex: Int, direction: Int, count: Int): Int? {
        if (currentIndex !in 0 until count || direction == 0) return null
        return (currentIndex + direction.sign()).takeIf { it in 0 until count }
    }

    private fun Int.sign(): Int = if (this > 0) 1 else -1
}
