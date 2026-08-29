package com.praveen.bchat.data.transfer

/**
 * Pure Kotlin Multiplatform IEEE 802.3 standard CRC-32 calculator.
 * Blazingly fast lookup-table based implementation with zero platform dependencies.
 */
object Crc32 {
    private val TABLE = LongArray(256) { i ->
        var c = i.toLong()
        for (j in 0 until 8) {
            c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1
        }
        c
    }

    fun calculate(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Long {
        var c = 0xFFFFFFFFL
        for (i in offset until (offset + length)) {
            val index = ((c xor bytes[i].toLong()) and 0xFFL).toInt()
            c = TABLE[index] xor (c ushr 8)
        }
        return (c xor 0xFFFFFFFFL) and 0xFFFFFFFFL
    }
}
