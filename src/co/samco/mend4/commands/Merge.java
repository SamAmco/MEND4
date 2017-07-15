package co.samco.mend4.commands;

import co.samco.mend4.core.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

public class Merge extends Command
{
    @Override
    public void execute(ArrayList<String> args)
    {
        if (printHelp(args))
            return;

        //first check if mend is unlocked
        File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
        if (!privateKeyFile.exists())
        {
            System.err.println("MEND is Locked. Please run mend unlock");
            return;
        }

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

        merge(firstLog, secondLog, deleteSecondLog);
    }

    private void merge(File firstLog, File secondLog, boolean deleteSecondLog)
    {
       //while first has next
            //while second has next && second < first
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
        private StringBuilder entryText;
        private Date dateTime;

        private LogEntry(StringBuilder entryText, Date dateTime)
        {
           this.entryText = entryText;
           this.dateTime = dateTime;
        }

        public LogEntry parseFromFile(File logFile)
        {
            try
            {
                boolean readNext = false;
                do
                {

                }
                while (readNext);
            }
            catch (Exception e)
            {
                System.err.println(e.getMessage());
            }
        }
    }
}
