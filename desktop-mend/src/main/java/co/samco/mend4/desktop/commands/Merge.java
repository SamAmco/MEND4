package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.EncryptionUtils;
import co.samco.mend4.core.Settings;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Merge extends Command {
    @Override
    public void execute(List<String> args) {
        if (printHelp(args))
            return;

        try {
            if (args.size() != 3) {
                System.err.println("Incorrect number of arguments");
                return;
            }
            if (args.get(0).equals("-2") || args.get(0).equals("-1")) {
                File firstLog = resolveLogFile(args.get(1));
                File secondLog = resolveLogFile(args.get(2));
                if (firstLog == null || secondLog == null)
                    return;
                mergeToFirstOrSecond(firstLog, secondLog, args.get(0));
                return;
            }

            File firstLog = resolveLogFile(args.get(0));
            File secondLog = resolveLogFile(args.get(1));
            if (firstLog == null || secondLog == null)
                return;
            mergeFilesToNew(firstLog, secondLog, new File(args.get(2)));
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private RSAPrivateKey getPrivateKey() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        //first check if mend is unlocked
        File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
        if (!privateKeyFile.exists())
            return null;

        return EncryptionUtils.getPrivateKeyFromFile(privateKeyFile);
    }

    private File resolveLogFile(String logName) throws Settings.UnInitializedSettingsException,
            Settings.InvalidSettingNameException, Settings.CorruptSettingsException {
        File logFile = new File(logName);
        if (!logFile.exists()) {
            String logDir = Settings.instance().getValue(Config.Settings.LOGDIR);
            if (logDir == null)
                return null;
            logFile = new File(logDir + File.separatorChar + logName);
            if (logFile.exists())
                return logFile;
            logFile = new File(logDir + File.separatorChar + logName + ".mend");
            if (logFile.exists())
                return logFile;
            System.err.println("Could not find log:\t" + logName);
            return null;
        }
        return logFile;
    }

    private void mergeToFirstOrSecond(File firstLog, File secondLog, String flag) {
        try {
            String logDir = Settings.instance().getValue(Config.Settings.LOGDIR);
            if (logDir == null)
                throw new IOException("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR
                        .ordinal())
                        + " property in your settings file before you can encrypt logs to it.");

            String tempName = "temp";
            int tempSuffix = 0;
            File currentOutFile = new File(logDir + File.separatorChar + tempName + tempSuffix + ".mend");
            while (currentOutFile.exists()) {
                tempSuffix++;
                currentOutFile = new File(logDir + tempName + tempSuffix + ".mend");
            }
            mergeFilesToNew(firstLog, secondLog, currentOutFile);
            if (flag.equals("-1"))
                Files.move(currentOutFile.toPath(), firstLog.toPath(), StandardCopyOption.REPLACE_EXISTING);
            else
                Files.move(currentOutFile.toPath(), secondLog.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | Settings.UnInitializedSettingsException | Settings.InvalidSettingNameException
                | Settings.CorruptSettingsException e) {
            System.err.println(e.getMessage());
        }
    }

    private void mergeFilesToNew(File firstLog, File secondLog, File outputLog) {
        FileInputStream f1InputStream = null;
        FileInputStream f2InputStream = null;
        FileOutputStream fOutputStream = null;
        try {
            f1InputStream = new FileInputStream(firstLog);
            f2InputStream = new FileInputStream(secondLog);
            outputLog.createNewFile();
            fOutputStream = new FileOutputStream(outputLog);
            RSAPrivateKey privateKey = getPrivateKey();
            if (privateKey == null) {
                System.err.println("MEND is Locked. Please run mend unlock");
                return;
            }
            //now we need an appropriate output file.
            mergeToOutputFile(privateKey, f1InputStream, f2InputStream, fOutputStream);
        } catch (java.io.IOException | InvalidKeyException | InvalidAlgorithmParameterException |
                NoSuchAlgorithmException
                | ParseException | NoSuchPaddingException | Settings.CorruptSettingsException |
                IllegalBlockSizeException
                | Settings.InvalidSettingNameException | BadPaddingException | EncryptionUtils.MalformedLogFileException
                | Settings.UnInitializedSettingsException | InvalidKeySpecException e) {
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

    private void mergeToOutputFile(RSAPrivateKey privateKey, FileInputStream firstLog, FileInputStream secondLog,
                                   FileOutputStream outputFile) throws IOException, ParseException,
            NoSuchAlgorithmException,
            EncryptionUtils.MalformedLogFileException, InvalidKeyException, InvalidAlgorithmParameterException,
            NoSuchPaddingException,
            BadPaddingException, Settings.InvalidSettingNameException, Settings.CorruptSettingsException,
            IllegalBlockSizeException,
            Settings.UnInitializedSettingsException {
        byte[] lc1Bytes = new byte[4];
        byte[] lc2Bytes = new byte[4];
        LogEntry secondLogEntry = null;
        if (EncryptionUtils.logHasNext(secondLog, lc2Bytes))
            secondLogEntry = LogEntry.parseNextLog(secondLog, privateKey, lc2Bytes);
        //while first has next
        while (EncryptionUtils.logHasNext(firstLog, lc1Bytes)) {
            LogEntry firstLogEntry = LogEntry.parseNextLog(firstLog, privateKey, lc1Bytes);
            //if first has no date
            //write first
            if (firstLogEntry.dateTime == null)
                outputFile.write(firstLogEntry.data);
            else {
                //while second has next && second < first || second has no date
                //write second
                while (secondLogEntry != null && (secondLogEntry.dateTime == null || secondLogEntry.dateTime.before
                        (firstLogEntry.dateTime))) {
                    outputFile.write(secondLogEntry.data);
                    if (EncryptionUtils.logHasNext(secondLog, lc2Bytes))
                        secondLogEntry = LogEntry.parseNextLog(secondLog, privateKey, lc2Bytes);
                    else secondLogEntry = null;
                }
            }
            //write first
            outputFile.write(firstLogEntry.data);
        }
        //now write out the rest of second
        while (secondLogEntry != null) {
            outputFile.write(secondLogEntry.data);
            if (EncryptionUtils.logHasNext(secondLog, lc2Bytes))
                secondLogEntry = LogEntry.parseNextLog(secondLog, privateKey, lc2Bytes);
            else secondLogEntry = null;
        }
    }

    @Override
    public String getUsageText() {
        return "Usage:\tmend merge [-1 | -2] <first log> <second log> [<output log>]";
    }

    @Override
    public String getDescriptionText() {
        return "Merges all the logs from two different log files into one log file, sorted by date.";
    }


    private static class LogEntry {
        private byte[] data;
        private Date dateTime = null;

        private LogEntry(byte[] data, Date dateTime) {
            this.data = data;
            this.dateTime = dateTime;
        }

        public static LogEntry parseNextLog(FileInputStream inputStream,
                                            RSAPrivateKey privateKey, byte[] lc1Bytes) throws IOException,
                InvalidKeyException,
                NoSuchAlgorithmException, Settings.InvalidSettingNameException, InvalidAlgorithmParameterException,
                NoSuchPaddingException, Settings.CorruptSettingsException, BadPaddingException, EncryptionUtils
                        .MalformedLogFileException,
                IllegalBlockSizeException, Settings.UnInitializedSettingsException, ParseException {
            EncryptionUtils.LogDataBlocksAndText nextLog = EncryptionUtils.getNextLogTextWithDataBlocks(inputStream,
                    privateKey, lc1Bytes);
            String logText = nextLog.entryText;
            Date firstDate = null;
            Pattern pattern = Pattern.compile("(\\d+)\\/(\\d+)\\/(\\d+) (\\d+):(\\d+):(\\d+)");
            Matcher matcher = pattern.matcher(logText);
            if (matcher.lookingAt()) {
                //then the log has a date, so let's parse the text to a Date object
                firstDate = (new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")).parse(matcher.group());
                //DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
                //System.out.println(matcher.group());
                //System.out.println(df.format(firstDate));
            }
            return new LogEntry(nextLog.logDataBlocks.getAsOneBlock(), firstDate);
        }
    }
}
