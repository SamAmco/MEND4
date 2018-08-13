package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.bean.LogDataBlocksAndText;
import co.samco.mend4.core.crypto.CryptoProvider;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.exception.MalformedLogFileException;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.exception.MendLockedException;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.apache.commons.lang3.tuple.Pair;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.StandardCopyOption;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MergeHelper {
    private final PrintStreamProvider log;
    private final I18N strings;
    private final FileResolveHelper fileResolveHelper;
    private final CryptoProvider cryptoProvider;
    private final KeyHelper keyHelper;
    private final OSDao osDao;
    private final Settings settings;

    @Inject
    public MergeHelper(PrintStreamProvider log, I18N strings, FileResolveHelper fileResolveHelper,
                       CryptoProvider cryptoProvider, KeyHelper keyHelper, OSDao osDao, Settings settings) {
        this.log = log;
        this.fileResolveHelper = fileResolveHelper;
        this.strings = strings;
        this.cryptoProvider = cryptoProvider;
        this.keyHelper = keyHelper;
        this.osDao = osDao;
        this.settings = settings;
    }

    public void mergeToFirstOrSecond(Pair<File, File> logFiles, boolean first) throws MendLockedException {
        try {
            File firstLog = logFiles.getLeft();
            File secondLog = logFiles.getRight();
            String logDir = settings.getValue(Settings.Name.LOGDIR);
            File tempFile = fileResolveHelper.getTempFile(logDir);
            mergeLogFilesToNew(logFiles, tempFile);
            if (first) {
                osDao.moveFile(tempFile.toPath(), firstLog.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.out().println(strings.getf("MergeHelper.movedFile",
                        tempFile.getAbsolutePath(), firstLog.getAbsolutePath()));
            } else {
                osDao.moveFile(tempFile.toPath(), secondLog.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.out().println(strings.getf("MergeHelper.movedFile",
                        tempFile.getAbsolutePath(), secondLog.getAbsolutePath()));
            }
        } catch (IOException | CorruptSettingsException e) {
            log.err().println(e.getMessage());
        }
    }

    public void mergeLogFilesToNew(Pair<File, File> files, File outputLog) throws MendLockedException {
        try (InputStream f1InputStream = osDao.getInputStreamForFile(files.getLeft());
            InputStream f2InputStream = osDao.getInputStreamForFile(files.getRight());
            OutputStream fOutputStream = osDao.getOutputStreamForFile(outputLog)) {
            osDao.createNewFile(outputLog);
            mergeLogs(f1InputStream, f2InputStream, fOutputStream);
            log.out().println(strings.getf("MergeHelper.mergeComplete", outputLog.getAbsolutePath()));
        } catch (ParseException | NoSuchAlgorithmException | BadPaddingException | MalformedLogFileException
                | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException | IOException
                | IllegalBlockSizeException | InvalidKeySpecException e) {
            log.err().println(e.getMessage());
        }
    }

    private void mergeLogs(InputStream firstLog, InputStream secondLog, OutputStream outputStream)
            throws IOException, ParseException, NoSuchAlgorithmException, MalformedLogFileException,
            InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            IllegalBlockSizeException, InvalidKeySpecException, MendLockedException {
        RSAPrivateKey privateKey = keyHelper.getPrivateKey();
        if (privateKey == null) {
            throw new MendLockedException();
        }
        LogEntry firstLogEntry = parseNextLog(firstLog, privateKey);
        LogEntry secondLogEntry = parseNextLog(secondLog, privateKey);
        LogEntry lastLogEntry = null;
        while (firstLogEntry != null || secondLogEntry != null) {
            if (firstLogEntry != null && firstLogEntry.before(secondLogEntry)) {
                writeIfNotDuplicate(firstLogEntry, lastLogEntry, outputStream);
                lastLogEntry = firstLogEntry;
                firstLogEntry = parseNextLog(firstLog, privateKey);
            } else {
                writeIfNotDuplicate(secondLogEntry, lastLogEntry, outputStream);
                lastLogEntry = secondLogEntry;
                secondLogEntry = parseNextLog(secondLog, privateKey);
            }
        }
    }

    private void writeIfNotDuplicate(LogEntry nextEntry, LogEntry lastEntry, OutputStream outputStream) throws IOException {
        if (lastEntry == null || !nextEntry.equals(lastEntry)) {
            outputStream.write(nextEntry.data);
        }
    }

    private LogEntry parseNextLog(InputStream inputStream, RSAPrivateKey privateKey)
            throws IOException, InvalidKeyException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            NoSuchPaddingException, BadPaddingException, MalformedLogFileException, IllegalBlockSizeException,
            ParseException {
        byte[] lc1Bytes = new byte[4];
        if (cryptoProvider.logHasNext(inputStream, lc1Bytes)) {
            return parseNextLog(inputStream, lc1Bytes, privateKey);
        } else {
            return null;
        }
    }

    private LogEntry parseNextLog(InputStream inputStream, byte[] lc1Bytes, RSAPrivateKey privateKey) throws
            NoSuchAlgorithmException, IOException, MalformedLogFileException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidKeyException,
            ParseException {
        LogDataBlocksAndText nextLog = cryptoProvider.getNextLogTextWithDataBlocks(inputStream,
                privateKey, lc1Bytes);
        String logText = nextLog.getEntryText();
        Date firstDate = null;
        Pattern pattern = Pattern.compile("(\\d+)\\/(\\d+)\\/(\\d+) (\\d+):(\\d+):(\\d+)");
        Matcher matcher = pattern.matcher(logText);
        if (matcher.lookingAt()) {
            firstDate = (new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")).parse(matcher.group());
        }
        return new LogEntry(nextLog.getLogDataBlocks().getAsOneBlock(), firstDate);
    }

    private static class LogEntry {
        private byte[] data;
        private Date dateTime;

        private LogEntry(byte[] data, Date dateTime) {
            this.data = data;
            this.dateTime = dateTime;
        }

        @Override
        public boolean equals(Object other) {
            LogEntry otherL = (LogEntry)other;
            if (dateTime == null || otherL.dateTime == null) {
                return false;
            } else return dateTime.equals(otherL.dateTime);
        }

        public boolean before(LogEntry other) {
            if(other == null) {
                return true;
            } else if (dateTime == null) {
                return true;
            } else if (other.dateTime == null) {
                return false;
            } else return dateTime.before(other.dateTime);
        }
    }
}
