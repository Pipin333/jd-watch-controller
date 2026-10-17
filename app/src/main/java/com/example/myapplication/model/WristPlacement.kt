package com.example.myapplication.model

/**
 * Represents the wrist placement of the Wear OS device for motion tracking.
 *
 * - [RIGHT_WRIST]: Default orientation on right arm.
 * - [LEFT_WRIST_MIRROR]: Placed on left wrist with X-axis mirrored for right-handed dance moves.
 */
enum class WristPlacement(val label: String) {
    RIGHT_WRIST("Mano Derecha"),
    LEFT_WRIST_MIRROR("Mano Izquierda");

    fun toggle(): WristPlacement = when (this) {
        RIGHT_WRIST -> LEFT_WRIST_MIRROR
        LEFT_WRIST_MIRROR -> RIGHT_WRIST
    }
}
