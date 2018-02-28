package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.EncryptionUtils;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import org.apache.commons.lang3.tuple.Pair;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TODO clean this up and test it
//TODO can we remove duplicate logs so we can safely merge the same two logs repeatedly
public class MergeHelper {
    private final FileResolveHelper fileResolveHelper;
    private final OSDao osDao;
    private final Settings settings;

    @Inject
    public MergeHelper(FileResolveHelper fileResolveHelper, OSDao osDao, Settings settings) {
        this.fileResolveHelper = fileResolveHelper;
        this.osDao = osDao;
        this.settings = settings;
    }

    public void mergeToFirstOrSecond(Pair<File, File> logFiles, boolean first) {
        try {
            File firstLog = logFiles.getLeft();
            File secondLog = logFiles.getRight();
            String logDir = settings.getValue(Settings.Name.LOGDIR);
            File tempFile = fileResolveHelper.getTempFile(logDir);
            mergeLogFilesToNew(logFiles, tempFile);
            if (first) {
                osDao.moveFile(tempFile.toPath(), firstLog.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                osDao.moveFile(tempFile.toPath(), secondLog.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | CorruptSettingsException e) {
            System.err.println(e.getMessage());
        }
    }

    public void mergeLogFilesToNew(Pair<File, File> files, File outputLog) {
        File firstLog = files.getLeft();
        File secondLog = files.getRight();
        FileInputStream f1InputStream = null;
        FileInputStream f2InputStream = null;
        FileOutputStream fOutputStream = null;
        try {
            f1InputStream = new FileInputStream(firstLog);
            f2InputStream = new FileInputStream(secondLog);
            outputLog.createNewFile();
            fOutputStream = new FileOutputStream(outputLog);
            mergeToOutputFile(f1InputStream, f2InputStream, fOutputStream);
        } catch (java.io.IOException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException
                | ParseException | NoSuchPaddingException |  IllegalBlockSizeException | BadPaddingException
                | EncryptionUtils.MalformedLogFileException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (f1InputStream != null)
                    f1InputStream.close();
                if (f2InputStream != null)
                    f2InputStream.close();
                if (fOutputStream != null)
                    fOutputStream.close();
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    public void mergeToOutputFile(FileInputStream firstLog, FileInputStream secondLog, FileOutputStream outputFile)
            throws IOException, ParseException, NoSuchAlgorithmException, EncryptionUtils.MalformedLogFileException,
            InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            IllegalBlockSizeException {
        byte[] lc1Bytes = new byte[4];
        byte[] lc2Bytes = new byte[4];
        LogEntry firstLogEntry = parseNextLog(firstLog, lc1Bytes);
        LogEntry secondLogEntry = parseNextLog(secondLog, lc2Bytes);
        boolean firstLogHasNext = EncryptionUtils.logHasNext(firstLog, lc1Bytes);
        boolean secondLogHasNext = EncryptionUtils.logHasNext(secondLog, lc2Bytes);

        while (firstLogHasNext || secondLogHasNext) {
            if (firstLogHasNext && firstLogEntry.before(secondLogEntry)) {
                outputFile.write(firstLogEntry.data);
                firstLogHasNext = EncryptionUtils.logHasNext(firstLog, lc1Bytes);
                if (firstLogHasNext) {
                    firstLogEntry = parseNextLog(firstLog, lc1Bytes);
                } else {
                    firstLogEntry = null;
                }
            } else if (secondLogHasNext) {
                outputFile.write(secondLogEntry.data);
                secondLogHasNext = EncryptionUtils.logHasNext(secondLog, lc2Bytes);
                if (secondLogHasNext) {
                    secondLogEntry = parseNextLog(secondLog, lc2Bytes);
                } else {
                    secondLogEntry = null;
                }
            }
        }
    }

    private LogEntry parseNextLog(FileInputStream inputStream, byte[] lc1Bytes)
            throws IOException, InvalidKeyException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            EncryptionUtils.MalformedLogFileException, IllegalBlockSizeException, ParseException {
        RSAPrivateKey privateKey = fileResolveHelper.getPrivateKey();
        EncryptionUtils.LogDataBlocksAndText nextLog = EncryptionUtils
                .getNextLogTextWithDataBlocks(inputStream, privateKey, lc1Bytes);
        String logText = nextLog.entryText;
        Date firstDate = null;
        Pattern pattern = Pattern.compile("(\\d+)\\/(\\d+)\\/(\\d+) (\\d+):(\\d+):(\\d+)");
        Matcher matcher = pattern.matcher(logText);
        if (matcher.lookingAt()) {
            firstDate = (new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")).parse(matcher.group());
        }
        return new LogEntry(nextLog.logDataBlocks.getAsOneBlock(), firstDate);
    }

    private static class LogEntry {
        private byte[] data;
        private Date dateTime;

        private LogEntry(byte[] data, Date dateTime) {
            this.data = data;
            this.dateTime = dateTime;
        }

        public boolean before(LogEntry other) {
            if(other == null) {
                return true;
            } else if (other.dateTime == null) {
                return false;
            } else if (dateTime == null) {
                return true;
            } else return dateTime.before(other.dateTime);
        }
    }
}
