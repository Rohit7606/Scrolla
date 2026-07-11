package com.scrolla.model

object DistanceFormatter {

    /** Converts raw pixel delta (from AccessibilityEvent) to cm using device DPI.
     *  Called by A's tracking layer on every scroll event. */
    fun pxToCm(deltaY: Int, ydpi: Float): Float {
        val cmPerPx = 2.54f / ydpi
        return Math.abs(deltaY) * cmPerPx
    }
}