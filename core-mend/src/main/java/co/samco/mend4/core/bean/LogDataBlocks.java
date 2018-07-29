package co.samco.mend4.core.bean;

import java.nio.ByteBuffer;

public class LogDataBlocks {
    private final byte[] encAesKey;
    private final byte[] lc2Bytes;
    private final byte[] encEntry;
    private final byte[] lc1Bytes;

    public LogDataBlocks(byte[] lc1Bytes, byte[] encAesKey, byte[] lc2Bytes, byte[] encEntry) {
        this.lc1Bytes = lc1Bytes;
        this.encAesKey = encAesKey;
        this.lc2Bytes = lc2Bytes;
        this.encEntry = encEntry;
    }

    public byte[] getEncAesKey() {
        return encAesKey;
    }

    public byte[] getLc2Bytes() {
        return lc2Bytes;
    }

    public byte[] getEncEntry() {
        return encEntry;
    }

    public byte[] getLc1Bytes() {
        return lc1Bytes;
    }

    public byte[] getAsOneBlock() {
        byte[] block = new byte[lc1Bytes.length + encAesKey.length + lc2Bytes.length + encEntry.length];
        ByteBuffer buffer = ByteBuffer.wrap(block);
        buffer.put(lc1Bytes);
        buffer.put(encAesKey);
        buffer.put(lc2Bytes);
        buffer.put(encEntry);
        return block;
    }
}
