package commands;

import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.desktop.commands.Merge;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MergeTest extends TestBase {
    private Merge merge;

    @Captor
    private ArgumentCaptor<Pair<File, File>> logFilesCaptor;

    @Before
    public void setup() {
        super.setup();
        merge = new Merge(strings, log, fileResolveHelper, mergeHelper, osDao);
    }

    @Test
    public void mergeToFirst() throws IOException, CorruptSettingsException {
        mergeInPlaceTest(true);
    }

    @Test
    public void mergeToSecond() throws IOException, CorruptSettingsException {
        mergeInPlaceTest(false);
    }

    @Test
    public void mergeToNewFile() throws IOException, CorruptSettingsException {
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

    private void mergeInPlaceTest(boolean first) throws IOException, CorruptSettingsException {
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

    private void resolveAnyFile() throws IOException, CorruptSettingsException {
        when(fileResolveHelper.resolveAsLogFilePath(anyString())).thenAnswer(
                (Answer<File>) invocation
                        -> new File((String)invocation.getArguments()[0]));
    }
}
