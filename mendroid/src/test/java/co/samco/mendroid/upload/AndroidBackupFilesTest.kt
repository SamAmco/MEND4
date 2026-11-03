package co.samco.mendroid.upload

import android.app.Application
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AndroidBackupFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var files: AndroidBackupFiles
    @Before fun setup() { files = AndroidBackupFiles(RuntimeEnvironment.getApplication()) }

    @Test fun appendKeepsExistingBytesAndClosesBeforeReturning() = runBlocking {
        val file = temporary.newFile("phone.mend").apply { writeText("old") }
        val source = LocalBackupFile(file.toURI().toString(), file.name, UploadKind.LOG)
        lateinit var output: OutputStream
        files.append(source) { output = it; it.write("new".toByteArray()) }
        assertEquals("oldnew", file.readText())
        try { output.write(1); fail("Append output must be closed") } catch (_: IOException) { }
    }

    @Test fun partialAppendFailureRestoresOriginalBytesAndAllowsANewAppend() = runBlocking {
        val file = temporary.newFile("phone.mend").apply { writeText("old encrypted entries") }
        val source = LocalBackupFile(file.toURI().toString(), file.name, UploadKind.LOG)
        val failure = IOException("disk full")
        try {
            files.append(source) { output -> output.write("half an entry".toByteArray()); throw failure }
            fail()
        } catch (e: IOException) { assertSame(failure, e) }
        assertEquals("old encrypted entries", file.readText())
        files.append(source) { it.write("complete entry".toByteArray()) }
        assertEquals("old encrypted entriescomplete entry", file.readText())
    }

    @Test fun cancellationRestoresTheOriginalBytes() = runBlocking {
        val file = temporary.newFile("phone.mend").apply { writeText("old") }
        val source = LocalBackupFile(file.toURI().toString(), file.name, UploadKind.LOG)
        val started = CompletableDeferred<Unit>()
        val writing = async {
            files.append(source) {
                it.write("partial".toByteArray())
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        started.await()
        writing.cancelAndJoin()
        assertEquals("old", file.readText())
    }
}
