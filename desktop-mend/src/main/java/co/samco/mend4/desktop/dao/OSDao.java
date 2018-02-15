package co.samco.mend4.desktop.dao;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Path;

public interface OSDao {
    File[] getDirectoryListing(File dirFile) throws FileNotFoundException;
    String getAbsolutePath(File file);
    Process executeCommand(String[] commandArgs) throws IOException;
    String getBaseName(File file);
    boolean fileExists(File file);
    boolean fileIsFile(File file);
    String getFileExtension(File file);
    String getFileExtension(String filePath);
    String getFileName(File file);
    InputStream getStdIn();
    Path moveFile(Path source, Path target, CopyOption... options) throws IOException;
}
