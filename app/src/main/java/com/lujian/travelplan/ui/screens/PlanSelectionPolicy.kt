package com.lujian.travelplan.ui.screens

object PlanSelectionPolicy {
    fun toggle(selectedIds: Set<Long>, planId: Long): Set<Long> =
        if (planId in selectedIds) selectedIds - planId else selectedIds + planId

    fun toggleAll(selectedIds: Set<Long>, planIds: Collection<Long>): Set<Long> {
        val allIds = planIds.toSet()
        return if (allIds.isNotEmpty() && selectedIds.containsAll(allIds)) emptySet() else allIds
    }
}
