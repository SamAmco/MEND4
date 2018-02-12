package commands;

import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.commands.Merge;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.MergeHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.System.out;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MergeTest {
    private MergeHelper mergeHelper;
    private I18N strings;
    private PrintStream err;
    private PrintStream out;
    private PrintStreamProvider log;
    private OSDao osDao;
    private FileResolveHelper fileResolveHelper;
    private Merge merge;

    @Captor
    private ArgumentCaptor<Pair<File, File>> logFilesCaptor;

    @Before
    public void setup() {
        mergeHelper = mock(MergeHelper.class);
        osDao = mock(OSDao.class);
        strings = new I18N("en", "UK");
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        log = mock(PrintStreamProvider.class);
        when(log.err()).thenReturn(err);
        when(log.out()).thenReturn(out);
        fileResolveHelper = mock(FileResolveHelper.class);
        merge = new Merge(strings, log, fileResolveHelper, mergeHelper, osDao);
    }

    @Test
    public void mergeToFirst() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        mergeInPlaceTest(true);
    }

    @Test
    public void mergeToSecond() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        mergeInPlaceTest(false);
    }

    @Test
    public void mergeToNewFile() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String logA = "logA";
        String logB = "logB";
        String logC = "logC";
        when(osDao.fileExists(any())).thenReturn(true);
        when(osDao.fileIsFile(any())).thenReturn(true);
        resolveAnyFile();
        merge.execute(Arrays.asList(logA, logB, logC));
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(mergeHelper).mergeLogFilesToNew(logFilesCaptor.capture(), fileCaptor.capture());

        Assert.assertEquals(logA, logFilesCaptor.getValue().getLeft().getName());
        Assert.assertEquals(logB, logFilesCaptor.getValue().getRight().getName());
        Assert.assertEquals(logC, fileCaptor.getValue().getName());
    }

    @Test
    public void tooManyArgs() {
        wrongArgNumTest(4);
    }

    @Test
    public void tooFewArgs() {
        wrongArgNumTest(2);
    }

    public void wrongArgNumTest(int argNum) {
        merge.execute(IntStream.of(argNum).mapToObj(i -> "arg").collect(Collectors.toList()));
        verify(err).println(strings.getf("General.invalidArgNum", Merge.COMMAND_NAME));
    }

    private void mergeInPlaceTest(boolean first) throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String logA = "logA";
        String logB = "logB";
        when(osDao.fileExists(any())).thenReturn(true);
        when(osDao.fileIsFile(any())).thenReturn(true);
        resolveAnyFile();
        merge.execute(Arrays.asList(first ? Merge.FIRST_FLAG : Merge.SECOND_FLAG, logA, logB));
        verify(mergeHelper).mergeToFirstOrSecond(logFilesCaptor.capture(), eq(first));

        Assert.assertEquals(logA, logFilesCaptor.getValue().getLeft().getName());
        Assert.assertEquals(logB, logFilesCaptor.getValue().getRight().getName());
    }

    private void resolveAnyFile() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        when(fileResolveHelper.resolveLogFilePath(anyString())).thenAnswer(
                (Answer<File>) invocation
                        -> new File((String)invocation.getArguments()[0]));
    }
}
