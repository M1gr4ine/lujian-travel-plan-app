package com.lujian.travelplan.ui.screens

internal fun <T> focusSingleMapInfoWindow(
    markers: Iterable<T>,
    isShown: (T) -> Boolean,
    hide: (T) -> Unit,
    center: (onFinished: () -> Unit) -> Unit,
    show: () -> Unit,
) {
    dismissVisibleMapInfoWindows(markers, isShown, hide)
    center(show)
}

internal fun <T> dismissVisibleMapInfoWindows(
    markers: Iterable<T>,
    isShown: (T) -> Boolean,
    hide: (T) -> Unit,
) {
    markers.filter(isShown).forEach(hide)
}

internal fun shouldDismissMapInfoWindowOnCameraMove(
    reason: Int,
    gestureReason: Int,
): Boolean = reason == gestureReason

internal fun shouldEnableDayPaging(mapDragEnabled: Boolean): Boolean = !mapDragEnabled

internal fun shouldEnableDailyMapListScroll(mapDragEnabled: Boolean): Boolean = !mapDragEnabled

internal fun applyDailyMapDragMode(
    dragEnabled: Boolean,
    cancelTransitions: () -> Unit,
    configureGestures: (scrollEnabled: Boolean, flingEnabled: Boolean) -> Unit,
) {
    if (dragEnabled) cancelTransitions()
    configureGestures(dragEnabled, false)
}
