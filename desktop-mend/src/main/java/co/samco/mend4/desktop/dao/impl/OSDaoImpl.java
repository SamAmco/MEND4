package co.samco.mend4.desktop.dao.impl;

import co.samco.mend4.core.OSDao;
import co.samco.mend4.desktop.core.I18N;
import org.apache.commons.io.FilenameUtils;

import javax.inject.Inject;
import java.awt.*;
import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

public class OSDaoImpl implements OSDao {
    private final I18N strings;

    @Inject
    public OSDaoImpl(I18N strings) {
        this.strings = strings;
    }

    @Override
    public String getUserHome() {
        return System.getProperty("user.home");
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
    public void createNewFile(File file) throws IOException {
        file.createNewFile();
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
    public OutputStream getOutputStreamForFile(File file, boolean append) throws FileNotFoundException {
        return new FileOutputStream(file, append);
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

    @Override
    public byte[] readAllFileBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    private void assertDirectoryExists(File file) throws FileNotFoundException {
        if (!file.exists() || !file.isDirectory() || file.listFiles() == null) {
            throw new FileNotFoundException(strings.getf("OSDaoImpl.dirNotFound", file.getAbsolutePath()));
        }
    }

}
