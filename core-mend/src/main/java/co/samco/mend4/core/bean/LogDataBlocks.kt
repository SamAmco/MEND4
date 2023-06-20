package co.samco.mend4.core.bean

import java.nio.ByteBuffer

data class LogDataBlocks(
    val lc1Bytes: ByteArray,
    val encAesKey: ByteArray,
    val lc2Bytes: ByteArray,
    val encEntry: ByteArray
) {

    val asOneBlock: ByteArray
        get() {
            val block = ByteArray(lc1Bytes.size + encAesKey.size + lc2Bytes.size + encEntry.size)
            val buffer = ByteBuffer.wrap(block)
            buffer.put(lc1Bytes)
            buffer.put(encAesKey)
            buffer.put(lc2Bytes)
            buffer.put(encEntry)
            return block
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LogDataBlocks

        if (!lc1Bytes.contentEquals(other.lc1Bytes)) return false
        if (!encAesKey.contentEquals(other.encAesKey)) return false
        if (!lc2Bytes.contentEquals(other.lc2Bytes)) return false
        if (!encEntry.contentEquals(other.encEntry)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lc1Bytes.contentHashCode()
        result = 31 * result + encAesKey.contentHashCode()
        result = 31 * result + lc2Bytes.contentHashCode()
        result = 31 * result + encEntry.contentHashCode()
        return result
    }
}