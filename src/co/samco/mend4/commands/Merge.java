package co.samco.mend4.commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.EncryptionUtils;
import co.samco.mend4.core.Settings;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
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
            //first check if mend is unlocked
            File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
            if (!privateKeyFile.exists())
            {
                System.err.println("MEND is Locked. Please run mend unlock");
                return;
            }
            RSAPrivateKey privateKey = EncryptionUtils.getPrivateKeyFromFile(privateKeyFile);

            boolean deleteSecondLog = false;
            if (args.get(0).equals("-d"))
            {
                deleteSecondLog = true;
                args.remove(0);
            }

            File firstLog = new File(args.get(0));
            File secondLog = new File(args.get(1));
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

            merge(privateKey, firstLog, secondLog, deleteSecondLog);
        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
        }
    }

    private void merge(RSAPrivateKey privateKey, File firstLog, File secondLog,
                       boolean deleteSecondLog)
    {
        FileInputStream f1InputStream = null;
        FileInputStream f2InputStream = null;

        try
        {
            f1InputStream = new FileInputStream(firstLog);
            f2InputStream = new FileInputStream(secondLog);
            byte[] lc1Bytes = new byte[4];
            f1InputStream.read(lc1Bytes);
            LogEntry.parseNextLog(f1InputStream, privateKey, lc1Bytes);
            //while first has next
            //if first has no date
            //write first
            //while second has next && second < first || second has no date
            //write second
            //write first
        }
        catch (java.io.IOException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException
                | ParseException | NoSuchPaddingException | Settings.CorruptSettingsException | IllegalBlockSizeException
                | Settings.InvalidSettingNameException | BadPaddingException | EncryptionUtils.MalformedLogFileException
                | Settings.UnInitializedSettingsException e)
        {
            System.err.println(e.getMessage());
        }
        finally
        {
            try
            {
                if (f1InputStream != null)
                    f1InputStream.close();
                if (f2InputStream != null)
                    f2InputStream.close();
            }
            catch (IOException e)
            {
                System.err.println(e.getMessage());
            }
        }
    }

    @Override
    public String getUsageText()
    {
        return "Usage:\tmend merge [-d] <first log> <second log>";
    }

    @Override
    public String getDescriptionText()
    {
        return "Takes all the logs from <second log> and attempts to parse them and sort them into <first log> (optionally deleting the second log)";
    }


    private static class LogEntry
    {
        private String entryText = "";
        private Date dateTime = null;

        private LogEntry(String entryText, Date dateTime)
        {
            this.entryText = entryText;
            this.dateTime = dateTime;
        }

        public static LogEntry parseNextLog(FileInputStream inputStream,
                                            RSAPrivateKey privateKey, byte[] lc1Bytes) throws IOException, InvalidKeyException,
                NoSuchAlgorithmException, Settings.InvalidSettingNameException, InvalidAlgorithmParameterException,
                NoSuchPaddingException, Settings.CorruptSettingsException, BadPaddingException, EncryptionUtils.MalformedLogFileException,
                IllegalBlockSizeException, Settings.UnInitializedSettingsException, ParseException
        {
            String logText = EncryptionUtils.getNextLogText(inputStream, privateKey, lc1Bytes);
            Date firstDate = null;
            Pattern pattern = Pattern.compile("(\\d+)\\/(\\d+)\\/(\\d+) (\\d+):(\\d+):(\\d+)");
            Matcher matcher = pattern.matcher(logText);
            if (matcher.find())
            {
                //then the log has a date, so let's parse the text to a Date object
                firstDate = (new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")).parse(matcher.group());
                //DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
                //System.out.println(matcher.group());
                //System.out.println(df.format(firstDate));
            }
            return new LogEntry(logText, firstDate);
        }
    }
}
