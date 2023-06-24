package helper

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.Settings
import co.samco.mend4.core.bean.LogDataBlocks
import co.samco.mend4.core.bean.LogDataBlocksAndText
import co.samco.mend4.core.util.LogUtils
import co.samco.mend4.desktop.exception.MendLockedException
import co.samco.mend4.desktop.helper.MergeHelper
import commands.TestBase
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.StandardCopyOption
import java.security.PrivateKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.LinkedList
import java.util.Locale
import java.util.Queue

class MergeHelperTest : TestBase() {
    private lateinit var uut: MergeHelper

    private lateinit var first: File
    private lateinit var second: File
    private lateinit var files: Pair<File, File>
    private lateinit var output: File
    private lateinit var firstWriter: Queue<String>
    private lateinit var secondWriter: Queue<String>
    private lateinit var firstStream: InputStream
    private lateinit var secondStream: InputStream
    private lateinit var outputStream: PipedOutputStream
    private lateinit var outputReader: InputStream

    @Before
    override fun setup() {
        super.setup()
        val privateKey: PrivateKey = mock()
        whenever(keyHelper.privateKey).thenReturn(privateKey)

        uut = MergeHelper(
            log = log,
            strings = strings,
            fileResolveHelper = fileResolveHelper,
            cryptoProvider = cryptoProvider,
            keyHelper = keyHelper,
            settings = settings,
            osDao = osDao
        )
    }

    private fun setupInputAndOutput() {
        first = File("first")
        second = File("second")
        files = Pair(first, second)
        output = File("output")
        firstWriter = LinkedList()
        secondWriter = LinkedList()
        outputStream = PipedOutputStream()
        firstStream = PipedInputStream()
        secondStream = PipedInputStream()
        outputReader = PipedInputStream(outputStream)
        doAnswer { getHasNextForInvocation(it) }
            .whenever(cryptoProvider)
            .logHasNext(any(), any())
        doAnswer { getNextLogForInvocation(it) }
            .whenever(cryptoProvider)
            .getNextLogTextWithDataBlocks(any(), any(), any())

        whenever(osDao.fileInputStream(first)).thenReturn(firstStream)
        whenever(osDao.fileInputStream(second)).thenReturn(secondStream)
        whenever(osDao.fileOutputSteam(output)).thenReturn(outputStream)
    }

    private fun getHasNextForInvocation(invocation: InvocationOnMock): Boolean {
        val args: Array<Any> = invocation.arguments
        val inputStream = args[0] as InputStream
        if (inputStream == firstStream) {
            return firstWriter.size > 0
        } else if (inputStream == secondStream) {
            return secondWriter.size > 0
        }
        return false
    }

    private fun getNextLogForInvocation(invocation: InvocationOnMock): LogDataBlocksAndText? {
        val args: Array<Any> = invocation.arguments
        val inputStream = args[0] as InputStream
        if (inputStream == firstStream) {
            return getLogForString(firstWriter.remove())
        } else if (inputStream == secondStream) {
            return getLogForString(secondWriter.remove())
        }
        return null
    }

    private fun getLogForString(message: String): LogDataBlocksAndText {
        val data = LogDataBlocks(
            message.toByteArray(charset("UTF-8")),
            ByteArray(0),
            ByteArray(0),
            ByteArray(0),
            ByteArray(0),
            ByteArray(0),
        )
        return LogDataBlocksAndText(data, message)
    }

    private fun writeToLog(logWriter: Queue<String>, message: String) {
        logWriter.add(message)
    }

    private fun getLogFromString(message: String): String {
        val sdf = SimpleDateFormat(AppProperties.LOG_DATE_FORMAT, Locale.ENGLISH)
        val cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, numExtraSeconds++)
        val time = sdf.format(cal.time)
        return LogUtils.addHeaderToLogText(message, "plat", time, "ver", "\n") + "\n"
    }

    private fun mergedOutput(): String {
        val mergedLogBytes = ByteArray(outputReader.available())
        outputReader.read(mergedLogBytes)
        return String(mergedLogBytes, StandardCharsets.UTF_8)
    }

    @Test(expected = MendLockedException::class)
    fun testMergeMendLocked() {
        setupInputAndOutput()
        whenever(keyHelper.privateKey).thenReturn(null)
        uut.mergeLogFilesToNew(files, output)
    }

    @Test(expected = MendLockedException::class)
    fun testMergeMendLockedMergeToExisting() {
        setupInputAndOutput()
        whenever(keyHelper.privateKey).thenReturn(null)
        uut.mergeToFirstOrSecond(files, true)
        verify(osDao, never()).renameFile(any(), any())
    }

    @Test
    fun testMerge() {
        setupInputAndOutput()
        val a = getLogFromString("s1")
        val b = getLogFromString("s2")
        val c = getLogFromString("s3")
        val d = getLogFromString("s4")
        writeToLog(firstWriter, a)
        writeToLog(secondWriter, b)
        writeToLog(firstWriter, c)
        writeToLog(secondWriter, d)
        uut.mergeLogFilesToNew(files, output)
        val output = mergedOutput()
        Assert.assertEquals(a + b + c + d, output)
    }

    @Test
    fun testMergeWithFirstEmpty() {
        setupInputAndOutput()
        val a = getLogFromString("s1")
        val b = getLogFromString("s2")
        writeToLog(firstWriter, a)
        writeToLog(firstWriter, b)
        uut.mergeLogFilesToNew(files, output)
        val output = mergedOutput()
        Assert.assertEquals(a + b, output)
    }

    @Test
    fun testMergeWithSecondLogEmpty() {
        setupInputAndOutput()
        val a = getLogFromString("s1")
        val b = getLogFromString("s2")
        writeToLog(secondWriter, a)
        writeToLog(secondWriter, b)
        uut.mergeLogFilesToNew(files, output)
        val output = mergedOutput()
        Assert.assertEquals(a + b, output)
    }

    @Test
    fun testBothLogsEmpty() {
        setupInputAndOutput()
        uut.mergeLogFilesToNew(files, output)
        val output = mergedOutput()
        Assert.assertEquals("", output)
    }

    @Test
    fun testDupsInOneFile() {
        setupInputAndOutput()
        val a = getLogFromString("s1")
        val b = getLogFromString("s2")
        val d = getLogFromString("s4")
        writeToLog(firstWriter, a)
        writeToLog(secondWriter, b)
        writeToLog(firstWriter, a)
        writeToLog(secondWriter, d)
        uut.mergeLogFilesToNew(files, output)
        val output = mergedOutput()
        Assert.assertEquals(a + b + d, output)
    }

    @Test
    fun testDupsAcrossFiles() {
        setupInputAndOutput()
        val a = getLogFromString("s1")
        val c = getLogFromString("s3")
        val d = getLogFromString("s4")
        writeToLog(firstWriter, a)
        writeToLog(secondWriter, a)
        writeToLog(firstWriter, c)
        writeToLog(secondWriter, d)
        uut.mergeLogFilesToNew(files, output)
        val output = mergedOutput()
        Assert.assertEquals(a + c + d, output)
    }

    @Test
    fun testMergeToFirst() {
        setupInputAndOutput()
        testMoveToFirstOrSecond(true, first)
    }

    @Test
    fun testMergeToSecond() {
        setupInputAndOutput()
        testMoveToFirstOrSecond(false, second)
    }

    private fun testMoveToFirstOrSecond(isFirst: Boolean, firstOrSecond: File) {
        val logdir = "logdir"
        val tempFile = File("temp")
        whenever(settings.getValue(Settings.Name.LOGDIR)).thenReturn(logdir)
        whenever(fileResolveHelper.getTempFile(logdir)).thenReturn(tempFile)
        uut.mergeToFirstOrSecond(files, isFirst)
        verify(fileResolveHelper).getTempFile(logdir)
        verify(osDao).move(
            tempFile.toPath(),
            firstOrSecond.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    @Test
    fun oneLogAllAppended() {
        setupInputAndOutput()
        val a = "s1"
        val b = getLogFromString("s2")
        val c = "s3"
        val d = getLogFromString("s4")
        writeToLog(firstWriter, a)
        writeToLog(secondWriter, b)
        writeToLog(firstWriter, c)
        writeToLog(secondWriter, d)
        uut.mergeLogFilesToNew(files, output)
        val output = mergedOutput()
        Assert.assertEquals(a + c + b + d, output)
    }

    @Test
    fun bothLogsAllAppended() {
        setupInputAndOutput()
        val a = "s1"
        val b = "s2"
        val c = "s3"
        val d = "s4"
        writeToLog(firstWriter, a)
        writeToLog(secondWriter, b)
        writeToLog(firstWriter, c)
        writeToLog(secondWriter, d)
        uut.mergeLogFilesToNew(files, output)
        val output = mergedOutput()
        Assert.assertEquals(a + c + b + d, output)
    }

    companion object {
        private var numExtraSeconds = 0
    }
}