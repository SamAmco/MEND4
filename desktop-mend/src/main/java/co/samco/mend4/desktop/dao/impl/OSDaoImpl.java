package co.samco.mend4.desktop.dao.impl;

import co.samco.mend4.desktop.dao.OSDao;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class OSDaoImpl implements OSDao {

    public OSDaoImpl() {}

    @Override
    public File[] getDirectoryListing(File dirFile) throws FileNotFoundException {
        File[] directoryListing = dirFile.listFiles();
        if (directoryListing == null) {
            throw new FileNotFoundException("Could not find the directory: " + dirFile.getAbsolutePath());
        }
        return directoryListing;
    }

    @Override
    public String getAbsolutePath(File file) {
        return file.getAbsolutePath();
    }

    @Override
    public Process executeCommand(String[] commandArgs) throws IOException {
        return Runtime.getRuntime().exec(commandArgs);
    }

    @Override
    public boolean fileExists(File file) {
        return file.exists();
    }

    @Override
    public boolean fileIsFile(File file) {
        return file.isFile();
    }

    @Override
    public String getFileExtension(File file) {
        return FilenameUtils.getExtension(file.getName());
    }

    @Override
    public String getFileExtension(String filePath) {
        return FilenameUtils.getExtension(filePath);
    }

    @Override
    public String getFileName(File file) {
        return file.getName();
    }

    @Override
    public InputStream getStdIn() {
        return System.in;
    }

}
