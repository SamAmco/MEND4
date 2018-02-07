package co.samco.mend4.desktop.dao.impl;

import co.samco.mend4.desktop.dao.OSDao;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class OSDaoImpl implements OSDao {

    @Inject
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
}
