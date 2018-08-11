package helper;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.bean.LogDataBlocks;
import co.samco.mend4.core.bean.LogDataBlocksAndText;
import co.samco.mend4.core.util.LogUtils;
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
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class MergeHelperTest extends TestBase {
    //TODO a merge with one empty log
    //TODO a merge with other empty log
    //TODO a merge with both empty logs
    //TODO a merge with duplicate logs in one file
    //TODO a merge with duplicate logs across files
    //TODO merge to first
    //TODO merge to second
    //TODO one or both files wont open
    //TODO one log all appended
    //TODO both logs all appended


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
            when(keyHelper.getPrivateKey()).thenReturn(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mergeHelper = new MergeHelper(log, fileResolveHelper, cryptoProvider, keyHelper, osDao, settings);
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

    @Test
    public void testMerge() throws IOException {
        String a = getLogFromString("s1");
        String b = getLogFromString("s2");
        String c = getLogFromString("s3");
        String d = getLogFromString("s4");
        setupInputAndOutput();
        writeToLog(firstWriter, a);
        writeToLog(secondWriter, b);
        writeToLog(firstWriter, c);
        writeToLog(secondWriter, d);
        mergeHelper.mergeLogFilesToNew(files, output);
        String output = getMergedOutput();
        Assert.assertEquals(a + b + c + d, output);
    }
}

















































