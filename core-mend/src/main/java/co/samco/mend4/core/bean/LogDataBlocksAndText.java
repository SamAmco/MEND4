package co.samco.mend4.core.bean;

public class LogDataBlocksAndText {
    private final LogDataBlocks logDataBlocks;
    private final String entryText;

    public LogDataBlocksAndText(LogDataBlocks logDataBlocks, String entryText) {
        this.logDataBlocks = logDataBlocks;
        this.entryText = entryText;
    }

    public LogDataBlocks getLogDataBlocks() {
        return logDataBlocks;
    }

    public String getEntryText() {
        return entryText;
    }
}
