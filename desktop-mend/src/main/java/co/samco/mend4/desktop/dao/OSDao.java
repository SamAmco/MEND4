package co.samco.mend4.desktop.dao;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public interface OSDao {
    File[] getDirectoryListing(File dirFile) throws FileNotFoundException;
    String getAbsolutePath(File file);
    Process executeCommand(String[] commandArgs) throws IOException;
}
