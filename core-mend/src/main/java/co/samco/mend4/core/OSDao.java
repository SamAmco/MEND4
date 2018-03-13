package co.samco.mend4.core;

import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Path;

public interface OSDao {
    String getUserHome();
    void desktopOpenFile(File file) throws IOException;
    void mkdirs(File file);
    File[] getDirectoryListing(File dirFile) throws FileNotFoundException;
    String getAbsolutePath(File file);
    Process executeCommand(String[] commandArgs) throws IOException;
    String getBaseName(File file);
    boolean fileExists(File file);
    boolean fileIsFile(File file);
    InputStream getInputStreamForFile(File file) throws FileNotFoundException;
    OutputStream getOutputStreamForFile(File file) throws FileNotFoundException;
    void writeDataToFile(byte[] data, File outputFile) throws IOException;
    String getFileExtension(File file);
    String getFileExtension(String filePath);
    InputStream getStdIn();
    char[] readPassword(String message);
    Path moveFile(Path source, Path target, CopyOption... options) throws IOException;
    void renameFile(File file, String newName);
    byte[] readAllFileBytes(Path path) throws IOException;
}
