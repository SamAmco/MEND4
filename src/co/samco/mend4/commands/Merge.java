package co.samco.mend4.commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.EncryptionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
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
        LogEntry.parseFromFile(firstLog);
        //while first has next
        //if first has no date
        //write first
        //while second has next && second < first || second has no date
        //write second
        //write first
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

        private LogEntry(StringBuilder entryText, Date dateTime)
        {
            this.entryText = entryText;
            this.dateTime = dateTime;
        }

        public static LogEntry parseFromFile(FileInputStream inputStream,
                                      RSAPrivateKey privateKey, byte[] lc1Bytes)
        {
            try
            {
                String logText = EncryptionUtils.getNextLogText(inputStream, privateKey, lc1Bytes);
                Date firstDate = null;
                Pattern pattern = Pattern.compile("\\\\d+\\\\/\\\\d+\\\\/\\\\d+ \\\\d+:\\\\d+:\\\\d+");
                Matcher matcher = pattern.matcher(logText);
                System.out.println(matcher.group());
            } catch (Exception e)
            {
                System.err.println(e.getMessage());
            }
        }
    }
}
