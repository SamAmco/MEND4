package commands

import co.samco.mend4.desktop.commands.Merge
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.stream.Collectors
import java.util.stream.IntStream

class MergeTest : TestBase() {
    private var merge: Merge? = null

    @Before
    override fun setup() {
        super.setup()
        merge = Merge(
            strings = strings,
            log = log,
            fileResolveHelper = fileResolveHelper,
            settingsHelper = settingsHelper,
            mergeHelper = mergeHelper
        )
    }

    @Test
    fun testCommandWithSettingsDependencies() {
        super.testCommandWithNoSettingsDependencies(merge!!)
    }

    @Test
    fun mergeToFirst() {
        mergeInPlaceTest(true)
    }

    @Test
    fun mergeToSecond() {
        mergeInPlaceTest(false)
    }

    @Test
    fun mergeToNewFile() {
        val logA = "logA"
        val logB = "logB"
        val logC = "logC"
        whenever(osDao.exists(any())).thenReturn(true)
        whenever(osDao.exists(any())).thenReturn(true)
        whenever(fileResolveHelper.ensureLogNameHasFileExtension(eq(logC))).thenReturn(logC)
        resolveAnyFile()
        merge!!.execute(listOf(logA, logB, logC))
        val fileCaptor = argumentCaptor<File>()
        val logFilesCaptor = argumentCaptor<Pair<File, File>>()
        verify(mergeHelper).mergeLogFilesToNew(logFilesCaptor.capture(), fileCaptor.capture())
        Assert.assertEquals(logA, logFilesCaptor.firstValue.first.name)
        Assert.assertEquals(logB, logFilesCaptor.firstValue.second.name)
        Assert.assertEquals(logC, fileCaptor.firstValue.name)
    }

    @Test
    fun tooManyArgs() {
        wrongArgNumTest(4)
    }

    @Test
    fun tooFewArgs() {
        wrongArgNumTest(2)
    }

    private fun wrongArgNumTest(argNum: Int) {
        merge!!.execute(IntStream.of(argNum).mapToObj { i: Int -> "arg" }
            .collect(Collectors.toList()))
        verify(err).println(strings.getf("General.invalidArgNum", Merge.COMMAND_NAME))
    }

    private fun mergeInPlaceTest(first: Boolean) {
        val logA = "logA"
        val logB = "logB"
        whenever(osDao.exists(any())).thenReturn(true)
        whenever(osDao.exists(any())).thenReturn(true)
        resolveAnyFile()
        merge!!.execute(
            listOf(
                if (first) Merge.FIRST_FLAG else Merge.SECOND_FLAG,
                logA,
                logB
            )
        )
        val logFilesCaptor = argumentCaptor<Pair<File, File>>()
        verify(mergeHelper).mergeToFirstOrSecond(logFilesCaptor.capture(), eq(first))
        Assert.assertEquals(logA, logFilesCaptor.firstValue.first.name)
        Assert.assertEquals(logB, logFilesCaptor.firstValue.second.name)
    }

    private fun resolveAnyFile() {
        whenever(fileResolveHelper.resolveAsLogFilePath(any())).thenAnswer { inv ->
            File(inv.arguments[0] as String)
        }
    }
}