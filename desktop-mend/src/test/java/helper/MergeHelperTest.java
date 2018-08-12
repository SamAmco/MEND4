package helper;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.bean.LogDataBlocks;
import co.samco.mend4.core.bean.LogDataBlocksAndText;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.util.LogUtils;
import co.samco.mend4.desktop.exception.MendLockedException;
import co.samco.mend4.desktop.helper.MergeHelper;
import commands.TestBase;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class MergeHelperTest extends TestBase {
    private MergeHelper mergeHelper;
    private File first;
    private File second;
    private Pair<File, File> files;
    private File output;
    private Queue<String> firstWriter;
    private Queue<String> secondWriter;
    private InputStream firstStream;
    private InputStream secondStream;
    private PipedOutputStream outputStream;
    private InputStream outputReader;

    @Before
    public void setup() {
        super.setup();
        try {
            RSAPrivateKey privateKey = mock(RSAPrivateKey.class);
            when(keyHelper.getPrivateKey()).thenReturn(privateKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mergeHelper = new MergeHelper(log, strings, fileResolveHelper, cryptoProvider, keyHelper, osDao, settings);
    }

    private void setupInputAndOutput() throws IOException {
        first = new File("first");
        second = new File("second");
        files = new ImmutablePair<>(first, second);
        output = new File("output");
        firstWriter = new LinkedList<>();
        secondWriter = new LinkedList<>();
        outputStream = new PipedOutputStream();
        firstStream = new PipedInputStream();
        secondStream = new PipedInputStream();
        outputReader = new PipedInputStream(outputStream);
        try {
            doAnswer(invocation -> getHasNextForInvocation(invocation)).when(cryptoProvider).logHasNext(any(), any());
            doAnswer(invocation -> getNextLogForInvocation(invocation)).when(cryptoProvider).getNextLogTextWithDataBlocks(any(), any(), any());
            when(osDao.getInputStreamForFile(first)).thenReturn(firstStream);
            when(osDao.getInputStreamForFile(second)).thenReturn(secondStream);
            when(osDao.getOutputStreamForFile(output)).thenReturn(outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean getHasNextForInvocation(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        InputStream inputStream = (InputStream)args[0];
        if (inputStream.equals(firstStream)) {
            return firstWriter.size() > 0;
        } else if (inputStream.equals(secondStream)) {
            return secondWriter.size() > 0;
        }
        return false;
    }

    private LogDataBlocksAndText getNextLogForInvocation(InvocationOnMock invocation) throws UnsupportedEncodingException {
        Object[] args = invocation.getArguments();
        InputStream inputStream = (InputStream)args[0];
        if (inputStream.equals(firstStream)) {
            return getLogForString(firstWriter.remove());
        } else if (inputStream.equals(secondStream)) {
            return getLogForString(secondWriter.remove());
        }
        return null;
    }

    private LogDataBlocksAndText getLogForString(String message) throws UnsupportedEncodingException {
        LogDataBlocks data = new LogDataBlocks(message.getBytes("UTF-8"), new byte[0], new byte[0], new byte[0]);
        return new LogDataBlocksAndText(data, message);
    }

    private void writeToLog(Queue<String> logWriter, String message) {
        logWriter.add(message);
    }

    private static int numExtraSeconds = 0;
    private String getLogFromString(String message) {
        SimpleDateFormat sdf = new SimpleDateFormat(AppProperties.LOG_DATE_FORMAT, Locale.ENGLISH);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, numExtraSeconds++);
        String time = sdf.format(cal.getTime());
        return LogUtils.addHeaderToLogText(message, "plat", time, "ver", "\n") + "\n";
    }

    private String getMergedOutput() throws IOException {
        byte[] mergedLogBytes = new byte[outputReader.available()];
        outputReader.read(mergedLogBytes);
        return new String(mergedLogBytes, StandardCharsets.UTF_8);
    }

    @Test(expected = MendLockedException.class)
    public void testMergeMendLocked() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, MendLockedException {
        setupInputAndOutput();
        when(keyHelper.getPrivateKey()).thenReturn(null);
        mergeHelper.mergeLogFilesToNew(files, output);
    }

    @Test(expected = MendLockedException.class)
    public void testMergeMendLockedMergeToExisting() throws NoSuchAlgorithmException, IOException,
            InvalidKeySpecException, MendLockedException {
        setupInputAndOutput();
        when(keyHelper.getPrivateKey()).thenReturn(null);
        mergeHelper.mergeToFirstOrSecond(files, true);
        verify(osDao, never()).renameFile(any(), any());
    }

    @Test
    public void testMerge() throws IOException, MendLockedException {
        setupInputAndOutput();
        String a = getLogFromString("s1");
        String b = getLogFromString("s2");
        String c = getLogFromString("s3");
        String d = getLogFromString("s4");
        writeToLog(firstWriter, a);
        writeToLog(secondWriter, b);
        writeToLog(firstWriter, c);
        writeToLog(secondWriter, d);
        mergeHelper.mergeLogFilesToNew(files, output);
        String output = getMergedOutput();
        Assert.assertEquals(a + b + c + d, output);
    }

    @Test
    public void testMergeWithFirstEmpty() throws IOException, MendLockedException {
        setupInputAndOutput();
        String a = getLogFromString("s1");
        String b = getLogFromString("s2");
        writeToLog(firstWriter, a);
        writeToLog(firstWriter, b);
        mergeHelper.mergeLogFilesToNew(files, output);
        String output = getMergedOutput();
        Assert.assertEquals(a + b, output);
    }

    @Test
    public void testMergeWithSecondLogEmpty() throws IOException, MendLockedException {
        setupInputAndOutput();
        String a = getLogFromString("s1");
        String b = getLogFromString("s2");
        writeToLog(secondWriter, a);
        writeToLog(secondWriter, b);
        mergeHelper.mergeLogFilesToNew(files, output);
        String output = getMergedOutput();
        Assert.assertEquals(a + b, output);
    }

    @Test
    public void testBothLogsEmpty() throws IOException, MendLockedException {
        setupInputAndOutput();
        mergeHelper.mergeLogFilesToNew(files, output);
        String output = getMergedOutput();
        Assert.assertEquals("", output);
    }

    @Test
    public void testDupsInOneFile() throws IOException, MendLockedException {
        setupInputAndOutput();
        String a = getLogFromString("s1");
        String b = getLogFromString("s2");
        String c = a;
        String d = getLogFromString("s4");
        writeToLog(firstWriter, a);
        writeToLog(secondWriter, b);
        writeToLog(firstWriter, c);
        writeToLog(secondWriter, d);
        mergeHelper.mergeLogFilesToNew(files, output);
        String output = getMergedOutput();
        Assert.assertEquals(a + b + d, output);
    }

    @Test
    public void testDupsAcrossFiles() throws IOException, MendLockedException {
        setupInputAndOutput();
        String a = getLogFromString("s1");
        String b = a;
        String c = getLogFromString("s3");
        String d = getLogFromString("s4");
        writeToLog(firstWriter, a);
        writeToLog(secondWriter, b);
        writeToLog(firstWriter, c);
        writeToLog(secondWriter, d);
        mergeHelper.mergeLogFilesToNew(files, output);
        String output = getMergedOutput();
        Assert.assertEquals(a + c + d, output);
    }

    @Test
    public void testMergeToFirst() throws IOException, CorruptSettingsException, MendLockedException {
        setupInputAndOutput();
        testMoveToFirstOrSecond(true, first);
    }

    @Test
    public void testMergeToSecond() throws IOException, CorruptSettingsException, MendLockedException {
        setupInputAndOutput();
        testMoveToFirstOrSecond(false, second);
    }

    private void testMoveToFirstOrSecond(boolean isFirst, File firstOrSecond) throws IOException, CorruptSettingsException, MendLockedException {
        String logdir = "logdir";
        File tempFile = new File("temp");
        when(settings.getValue(Settings.Name.LOGDIR)).thenReturn(logdir);
        when(fileResolveHelper.getTempFile(logdir)).thenReturn(tempFile);
        mergeHelper.mergeToFirstOrSecond(files, isFirst);
        verify(fileResolveHelper).getTempFile(logdir);
        verify(osDao).moveFile(tempFile.toPath(), firstOrSecond.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void oneLogAllAppended() throws IOException, MendLockedException {
        setupInputAndOutput();
        String a = "s1";
        String b = getLogFromString("s2");
        String c = "s3";
        String d = getLogFromString("s4");
        writeToLog(firstWriter, a);
        writeToLog(secondWriter, b);
        writeToLog(firstWriter, c);
        writeToLog(secondWriter, d);
        mergeHelper.mergeLogFilesToNew(files, output);
        String output = getMergedOutput();
        Assert.assertEquals(a + c + b + d, output);
    }

    @Test
    public void bothLogsAllAppended() throws IOException, MendLockedException {
        setupInputAndOutput();
        String a = "s1";
        String b = "s2";
        String c = "s3";
        String d = "s4";
        writeToLog(firstWriter, a);
        writeToLog(secondWriter, b);
        writeToLog(firstWriter, c);
        writeToLog(secondWriter, d);
        mergeHelper.mergeLogFilesToNew(files, output);
        String output = getMergedOutput();
        Assert.assertEquals(a + c + b + d, output);
    }
}
