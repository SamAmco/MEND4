package co.samco.mendroid.upload

import android.app.Application
import androidx.documentfile.provider.DocumentFile
import co.samco.mendroid.model.LocalLogDirectory
import co.samco.mendroid.model.LogFileManagerImpl
import co.samco.mendroid.model.PropertyManagerImpl
import co.samco.mendroid.nextcloud.RemotePath
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
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
class DirectoryLogBackupTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var env: UploadTestEnvironment

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("nextcloud-uploads.db")
        env = UploadTestEnvironment(context)
        env.connect()
    }
    @After fun teardown() { env.queue.close() }

    @Test fun directoryLogAppendsLocallyAndCoalescesBackupUnderItsActualFilename() = runBlocking {
        val folder = temporary.newFolder()
        val properties = PropertyManagerImpl(RuntimeEnvironment.getApplication())
        properties.setLogDirUri(folder.toURI().toString())
        val logs = LogFileManagerImpl(LocalLogDirectory { DocumentFile.fromFile(folder) }, properties)
        logs.create("phone")
        val log = requireNotNull(logs.currentForWrite())
        val source = File(folder, log.name)
        env.files.sources[log.uri.toString()] = source
        val file = LocalBackupFile(log.uri.toString(), log.name, UploadKind.LOG)
        repeat(2) { index ->
            val saved = env.writer.write(file, WriteMode.APPEND) { it.write("encrypted-$index".toByteArray()) }
            assertEquals(BackupRegistration.QUEUED, saved.backup)
        }
        assertEquals("encrypted-0encrypted-1", source.readText())
        val pending = env.queue.pending().single()
        assertEquals(2L, pending.revision.number)
        assertEquals("phone.mend", pending.file.name)
        assertEquals(RemotePath.of("/logs/phone.mend"), pending.target.path)
    }
}
