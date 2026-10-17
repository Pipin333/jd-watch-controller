package com.example.myapplication.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes sensor data into a compact 21-byte binary frame using Little Endian byte order.
 *
 * Frame layout [Total 21 bytes]:
 * - Type (Byte): 1 byte -> 0x01 = Accelerometer, 0x02 = Heart Rate
 * - Timestamp (Long): 8 bytes -> System timestamp in millis
 * - X (Float): 4 bytes
 * - Y (Float): 4 bytes
 * - Z (Float): 4 bytes
 */
object BinaryPacketEncoder {

    const val TYPE_ACCELEROMETER: Byte = 0x01
    const val TYPE_HEART_RATE: Byte = 0x02
    const val PACKET_SIZE_BYTES: Int = 21

    /**
     * Encodes 3-axis accelerometer readings into a 21-byte Little Endian ByteArray.
     */
    fun encodeAccelerometer(
        timestamp: Long,
        x: Float,
        y: Float,
        z: Float
    ): ByteArray {
        return ByteBuffer.allocate(PACKET_SIZE_BYTES).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(TYPE_ACCELEROMETER)
            putLong(timestamp)
            putFloat(x)
            putFloat(y)
            putFloat(z)
        }.array()
    }

    /**
     * Encodes heart rate BPM reading into a 21-byte Little Endian ByteArray.
     */
    fun encodeHeartRate(
        timestamp: Long,
        bpm: Float
    ): ByteArray {
        return ByteBuffer.allocate(PACKET_SIZE_BYTES).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(TYPE_HEART_RATE)
            putLong(timestamp)
            putFloat(bpm)
            putFloat(0f)
            putFloat(0f)
        }.array()
    }
}
