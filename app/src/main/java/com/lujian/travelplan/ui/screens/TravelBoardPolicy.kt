package com.lujian.travelplan.ui.screens

import com.lujian.travelplan.data.StoredPlan

enum class TravelBoard(
    val title: String,
    val subtitle: String,
) {
    PLANS("计划板", "把下一次出发，钉在这里"),
    FOOTPRINTS("足迹板", "走过的旅程，都留有一枚脚印"),
}

object TravelBoardPolicy {
    fun plansFor(board: TravelBoard, plans: List<StoredPlan>): List<StoredPlan> = when (board) {
        TravelBoard.PLANS -> plans.filter { it.archivedAt == null }
        TravelBoard.FOOTPRINTS -> plans.filter { it.archivedAt != null }
    }

    fun archiveValue(board: TravelBoard): Boolean = board == TravelBoard.PLANS
}
