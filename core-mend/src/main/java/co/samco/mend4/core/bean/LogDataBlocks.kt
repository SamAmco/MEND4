package co.samco.mend4.core.bean

import java.nio.ByteBuffer

data class LogDataBlocks(
    val lc1Bytes: ByteArray,
    val encKey: ByteArray,
    val lc2Bytes: ByteArray,
    val iv: ByteArray,
    val lc3Bytes: ByteArray,
    val encEntry: ByteArray
) {

    val asOneBlock: ByteArray
        get() {
            val list = listOf(lc1Bytes, encKey, lc2Bytes, iv, lc3Bytes, encEntry)
            val block = ByteArray(list.sumOf { it.size })
            val buffer = ByteBuffer.wrap(block)
            list.forEach { buffer.put(it) }
            return block
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LogDataBlocks

        if (!lc1Bytes.contentEquals(other.lc1Bytes)) return false
        if (!encKey.contentEquals(other.encKey)) return false
        if (!lc2Bytes.contentEquals(other.lc2Bytes)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!lc3Bytes.contentEquals(other.lc3Bytes)) return false
        if (!encEntry.contentEquals(other.encEntry)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lc1Bytes.contentHashCode()
        result = 31 * result + encKey.contentHashCode()
        result = 31 * result + lc2Bytes.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + lc3Bytes.contentHashCode()
        result = 31 * result + encEntry.contentHashCode()
        return result
    }
}