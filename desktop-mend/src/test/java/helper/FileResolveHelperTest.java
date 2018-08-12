package helper;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import commands.TestBase;
import dagger.Lazy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class FileResolveHelperTest extends TestBase {

    @Mock
    private Lazy<Settings> lazySettings;

    @Before
    public void setup() {
        super.setup();
        when(lazySettings.get()).thenReturn(settings);
        fileResolveHelper = new FileResolveHelper(osDao, lazySettings, strings);
    }

    @Test
    public void testResolveAsLogFilePathAbsolute() throws IOException, CorruptSettingsException {
        String filePath = File.separator + "sam" + File.separator + "file." + AppProperties.LOG_FILE_EXTENSION;
        when(osDao.getFileExtension(eq(filePath))).thenReturn(AppProperties.LOG_FILE_EXTENSION);
        when(osDao.fileExists(eq(new File(filePath)))).thenReturn(true);
        File output = fileResolveHelper.resolveAsLogFilePath(filePath);
        Assert.assertEquals(filePath, output.getAbsolutePath());
    }

    @Test
    public void testResolveAsLogFilePathInLogDir() throws IOException, CorruptSettingsException {
        String filePath = "logFile." + AppProperties.LOG_FILE_EXTENSION;
        String logDir = File.separator + "logdir";
        when(osDao.fileExists(eq(new File(filePath)))).thenReturn(false);
        when(osDao.getFileExtension(eq(filePath))).thenReturn(AppProperties.LOG_FILE_EXTENSION);
        when(settings.getValue(Settings.Name.LOGDIR)).thenReturn(logDir);
        File output = fileResolveHelper.resolveAsLogFilePath(filePath);
        Assert.assertEquals(logDir + File.separator + filePath,
                output.getAbsolutePath());
    }

    @Test
    public void testResolveAsLogFilePathInLogDirNoExtension() throws IOException, CorruptSettingsException {
        String filePath = "logFile";
        String logDir = File.separator + "logdir";
        when(osDao.fileExists(eq(new File(filePath)))).thenReturn(false);
        when(osDao.getFileExtension(eq(filePath))).thenReturn("");
        when(settings.getValue(Settings.Name.LOGDIR)).thenReturn(logDir);
        File output = fileResolveHelper.resolveAsLogFilePath(filePath);
        Assert.assertEquals(logDir + File.separator + filePath + "." + AppProperties.LOG_FILE_EXTENSION,
                output.getAbsolutePath());
    }

    @Test
    public void testGetTempFile() {
        String dir = File.separator + "dirName";
        when(osDao.fileExists(any())).thenReturn(false);
        File outputFile = fileResolveHelper.getTempFile(dir);
        Assert.assertEquals(dir + File.separator + "tmp0." + AppProperties.LOG_FILE_EXTENSION,
                outputFile.getAbsolutePath());
    }

    @Test
    public void testGetTempFileDontOverwrite() {
        String dir = File.separator + "dirName";
        when(osDao.fileExists(any())).thenReturn(true, true, false);
        File outputFile = fileResolveHelper.getTempFile(dir);
        Assert.assertEquals(dir + File.separator + "tmp2." + AppProperties.LOG_FILE_EXTENSION,
                outputFile.getAbsolutePath());
    }
}









































