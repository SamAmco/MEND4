package co.samco.mend4.desktop.dao.impl;

import co.samco.mend4.desktop.dao.OSDao;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

//TODO clean this up, take strings out etc
public class OSDaoImpl implements OSDao {

    public OSDaoImpl() {}

    @Override
    public File[] getDirectoryListing(File dirFile) throws FileNotFoundException {
        assertDirectoryExists(dirFile);
        return dirFile.listFiles();
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
    public String getBaseName(File file) {
        return FilenameUtils.getBaseName(file.getAbsolutePath());
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

    @Override
    public Path moveFile(Path source, Path target, CopyOption... options) throws IOException {
        return Files.move(source, target, options);
    }

    private void assertDirectoryExists(File file) throws FileNotFoundException {
        if (!file.exists() || !file.isDirectory() || file.listFiles() == null) {
            throw new FileNotFoundException("Could not find the directory: " + file.getAbsolutePath());
        }
    }

}
