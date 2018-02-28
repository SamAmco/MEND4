package co.samco.mend4.desktop.dao.impl;

import co.samco.mend4.core.OSDao;
import org.apache.commons.io.FilenameUtils;

import java.awt.*;
import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

//TODO clean this up, take strings out etc
public class OSDaoImpl implements OSDao {
    private String userHomeCached;

    public OSDaoImpl() {}

    @Override
    public String getUserHome() {
        if (userHomeCached == null) {
            userHomeCached = System.getProperty("user.home");
        }
        return userHomeCached;
    }

    @Override
    public void desktopOpenFile(File file) throws IOException {
        Desktop.getDesktop().open(file);
    }

    @Override
    public void mkdirs(File file) {
       file.mkdirs();
    }

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
        return file.exists() && file.isFile();
    }

    @Override
    public boolean fileIsFile(File file) {
        return file.isFile();
    }

    @Override
    public InputStream getInputStreamForFile(File file) throws FileNotFoundException {
        return new FileInputStream(file);
    }

    @Override
    public OutputStream getOutputStreamForFile(File file) throws FileNotFoundException {
        return new FileOutputStream(file);
    }

    @Override
    public void writeDataToFile(byte[] data, File outputFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(data);
        }
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
    public InputStream getStdIn() {
        return System.in;
    }

    @Override
    public char[] readPassword(String message) {
        return System.console().readPassword(message);
    }

    @Override
    public Path moveFile(Path source, Path target, CopyOption... options) throws IOException {
        return Files.move(source, target, options);
    }

    @Override
    public void renameFile(File file, String newName) {
        file.renameTo(new File(newName));
    }

    private void assertDirectoryExists(File file) throws FileNotFoundException {
        if (!file.exists() || !file.isDirectory() || file.listFiles() == null) {
            throw new FileNotFoundException("Could not find the directory: " + file.getAbsolutePath());
        }
    }

}
