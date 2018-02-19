package co.samco.mend4.desktop.dao;

import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Path;

public interface OSDao {
    void mkdirs(File file);
    File[] getDirectoryListing(File dirFile) throws FileNotFoundException;
    String getAbsolutePath(File file);
    Process executeCommand(String[] commandArgs) throws IOException;
    String getBaseName(File file);
    boolean fileExists(File file);
    boolean fileIsFile(File file);
    String getFileExtension(File file);
    String getFileExtension(String filePath);
    InputStream getStdIn();
    char[] readPassword(String message);
    Path moveFile(Path source, Path target, CopyOption... options) throws IOException;
}
