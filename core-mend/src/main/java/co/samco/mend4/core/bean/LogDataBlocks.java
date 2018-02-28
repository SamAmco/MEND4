package co.samco.mend4.core.bean;

import org.apache.commons.lang3.ArrayUtils;

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

    //TODO there is probably a faster way to do this.
    public byte[] getAsOneBlock() {
        return ArrayUtils.addAll(
                ArrayUtils.addAll(lc1Bytes, encAesKey),
                ArrayUtils.addAll(lc2Bytes, encEntry)
        );
    }
}
