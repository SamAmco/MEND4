package co.samco.mend4.commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.EncryptionUtils;
import co.samco.mend4.core.Settings;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Merge extends Command
{
    @Override
    public void execute(ArrayList<String> args)
    {
        if (printHelp(args))
            return;

        try
        {
            //boolean deleteSecondLog = false;
            //if (args.get(0).equals("-d"))
            //{
            //    deleteSecondLog = true;
            //    args.remove(0);
            //}
            mergeFilesToNew(args.get(0), args.get(1), args.get(2));
        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
        }
    }

    private RSAPrivateKey getPrivateKey() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException
    {
       //first check if mend is unlocked
       File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
       if (!privateKeyFile.exists())
           return null;

       return EncryptionUtils.getPrivateKeyFromFile(privateKeyFile);
    }

    private void mergeFilesToNew(String firstLogName, String secondLogName, String outputLogName)
    {
        File firstLog = new File(firstLogName);
        File secondLog = new File(secondLogName);
        File outputLog = new File(outputLogName);
        if (!firstLog.exists())
        {
            System.err.println("Could not find file:\t" + firstLog.getAbsolutePath());
            return;
        }
        if (!secondLog.exists())
        {
            System.err.println("Could not find file:\t" + secondLog.getAbsolutePath());
            return;
        }
        if (!firstLog.canRead())
        {
            System.err.println("You do not have permission to read:\t" + firstLog.getAbsolutePath());
            return;
        }
        if (!secondLog.canRead())
        {
            System.err.println("You do not have permission to read:\t" + secondLog.getAbsolutePath());
            return;
        }

        FileInputStream f1InputStream = null;
        FileInputStream f2InputStream = null;
        FileOutputStream fOutputStream = null;
        try
        {
            f1InputStream = new FileInputStream(firstLog);
            f2InputStream = new FileInputStream(secondLog);
            outputLog.createNewFile();
            fOutputStream = new FileOutputStream(outputLog);
            RSAPrivateKey privateKey = getPrivateKey();
            if (privateKey == null)
            {
                System.err.println("MEND is Locked. Please run mend unlock");
                return;
            }
            //now we need an appropriate output file.
            mergeToOutputFile(privateKey, f1InputStream, f2InputStream, fOutputStream);
        }
        catch (java.io.IOException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException
            | ParseException | NoSuchPaddingException | Settings.CorruptSettingsException | IllegalBlockSizeException
            | Settings.InvalidSettingNameException | BadPaddingException | EncryptionUtils.MalformedLogFileException
            | Settings.UnInitializedSettingsException | InvalidKeySpecException e)
        {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if (f1InputStream != null)
                    f1InputStream.close();
                if (f2InputStream != null)
                    f2InputStream.close();
                if (fOutputStream != null)
                    fOutputStream.close();
            }
            catch (IOException e)
            {
                System.err.println(e.getMessage());
            }
        }
    }

    private void mergeToOutputFile(RSAPrivateKey privateKey, FileInputStream firstLog, FileInputStream secondLog,
                                   FileOutputStream outputFile) throws IOException, ParseException, NoSuchAlgorithmException,
            EncryptionUtils.MalformedLogFileException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException,
            BadPaddingException, Settings.InvalidSettingNameException, Settings.CorruptSettingsException, IllegalBlockSizeException,
            Settings.UnInitializedSettingsException
    {
        byte[] lc1Bytes = new byte[4];
        byte[] lc2Bytes = new byte[4];
        LogEntry secondLogEntry = null;
        if (EncryptionUtils.logHasNext(secondLog, lc2Bytes))
            secondLogEntry = LogEntry.parseNextLog(secondLog, privateKey, lc2Bytes);
        //while first has next
        while (EncryptionUtils.logHasNext(firstLog, lc1Bytes))
        {
            LogEntry firstLogEntry = LogEntry.parseNextLog(firstLog, privateKey, lc1Bytes);
            //if first has no date
            //write first
            if (firstLogEntry.dateTime == null)
                outputFile.write(firstLogEntry.data);
            else
            {
                //while second has next && second < first || second has no date
                //write second
                while (secondLogEntry != null && (secondLogEntry.dateTime == null || secondLogEntry.dateTime.before(firstLogEntry.dateTime)))
                {
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
        while (secondLogEntry != null)
        {
            outputFile.write(secondLogEntry.data);
            if (EncryptionUtils.logHasNext(secondLog, lc2Bytes))
                secondLogEntry = LogEntry.parseNextLog(secondLog, privateKey, lc2Bytes);
            else secondLogEntry = null;
        }
    }

    @Override
    public String getUsageText()
    {
        return "Usage:\tmend merge [-d] <first log> <second log>";
    }

    @Override
    public String getDescriptionText() { return "Takes all the logs from <second log> and attempts to parse them and sort them into <first log> (optionally deleting the second log)"; }


    private static class LogEntry
    {
        private byte[] data;
        private Date dateTime = null;

        private LogEntry(byte[] data, Date dateTime)
        {
            this.data = data;
            this.dateTime = dateTime;
        }

        public static LogEntry parseNextLog(FileInputStream inputStream,
                                            RSAPrivateKey privateKey, byte[] lc1Bytes) throws IOException, InvalidKeyException,
                NoSuchAlgorithmException, Settings.InvalidSettingNameException, InvalidAlgorithmParameterException,
                NoSuchPaddingException, Settings.CorruptSettingsException, BadPaddingException, EncryptionUtils.MalformedLogFileException,
                IllegalBlockSizeException, Settings.UnInitializedSettingsException, ParseException
        {
            EncryptionUtils.LogDataBlocksAndText nextLog = EncryptionUtils.getNextLogTextWithDataBlocks(inputStream, privateKey, lc1Bytes);
            String logText = nextLog.entryText;
            Date firstDate = null;
            Pattern pattern = Pattern.compile("(\\d+)\\/(\\d+)\\/(\\d+) (\\d+):(\\d+):(\\d+)");
            Matcher matcher = pattern.matcher(logText);
            if (matcher.lookingAt())
            {
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
