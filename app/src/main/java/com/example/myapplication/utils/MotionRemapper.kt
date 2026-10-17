package com.example.myapplication.utils

import com.example.myapplication.model.WristPlacement

/**
 * Axis remapping module to transform raw 3-axis accelerometer sensor readings
 * based on wrist placement (Right Wrist vs Left Wrist Mirroring).
 */
object MotionRemapper {

    /**
     * Remaps raw accelerometer [rawX, rawY, rawZ] according to [WristPlacement].
     *
     * - [WristPlacement.RIGHT_WRIST]: Retains original accelerometer axes.
     * - [WristPlacement.LEFT_WRIST_MIRROR]: Mirrors X axis (x = -rawX) so that left arm
     *   gestures align with right arm coordinate reference in dance games.
     */
    fun remapAccelerometer(
        rawX: Float,
        rawY: Float,
        rawZ: Float,
        wristPlacement: WristPlacement
    ): FloatArray {
        return when (wristPlacement) {
            WristPlacement.RIGHT_WRIST -> floatArrayOf(rawX, rawY, rawZ)
            WristPlacement.LEFT_WRIST_MIRROR -> floatArrayOf(-rawX, rawY, rawZ)
        }
    }
}
