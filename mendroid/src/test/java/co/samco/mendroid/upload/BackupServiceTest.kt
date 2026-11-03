package co.samco.mendroid.upload

import android.app.Application
import co.samco.mendroid.nextcloud.NextcloudSession
import co.samco.mendroid.nextcloud.RemoteFolder
import co.samco.mendroid.nextcloud.RemoteFolderBrowser
import co.samco.mendroid.nextcloud.RemotePath
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BackupServiceTest {
    private lateinit var env: UploadTestEnvironment
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("nextcloud-uploads.db")
        env = UploadTestEnvironment(context)
        env.connect()
    }
    @After fun teardown() { env.queue.close() }
    private fun service(existing: List<LocalBackupFile> = emptyList()): BackupService {
        val runner = UploadRunner(env.queue, env.snapshots, UploadEligibility(env.settings, env.credentials, NetworkStatus { false }), TestTransport())
        return BackupService(env.settings, env.credentials, env.queue, env.writer, ExistingBackupSource { existing },
            object : RemoteFolderBrowser {
                override suspend fun folders(session: NextcloudSession, path: RemotePath) = emptyList<RemoteFolder>()
            }, env.wakeup, runner)
    }
    @Test fun policyChangesReplaceScheduledWorkInsteadOfAppendingBehindOldConstraints() = runBlocking {
        val service = service()
        service.allowMobileData(true)
        assertTrue(env.settings.config.value.mobileData)
        service.pause(true)
        assertTrue(env.settings.config.value.paused)
        service.pause(false)
        assertFalse(env.settings.config.value.paused)
        service.disconnect()
        assertNull(env.settings.config.value.account)
        assertEquals(4, env.wakeup.reschedules)
    }

    @Test fun explicitVerificationRetainsOriginalDestinationAfterSettingsChange() = runBlocking {
        val file = env.files.source()
        val original = requireNotNull(env.settings.config.value.target(file))
        env.queue.failWrite(env.queue.beginWrite(file, original))
        val failed = env.queue.entries.value.single()
        env.settings.selectLogFolder(RemotePath.of("/different"))
        val service = service()
        service.retry()
        assertTrue(env.queue.pending().isEmpty())
        service.backupVerified(failed)
        assertEquals(original, env.queue.pending().single().target)
    }
    @Test fun oldFileBackupDoesNotRewriteLocalFiles() = runBlocking {
        val file = env.files.source(content = "old ciphertext")
        env.settings.recordUnqueuedSave()
        service(listOf(file)).backupExisting()
        assertFalse(env.settings.config.value.needsRescan)
        assertEquals(1, env.queue.pending().size)
        assertFalse(env.files.outputClosed)
        assertEquals("old ciphertext", env.files.sources.getValue(file.uri).readText())
    }
    @Test fun bulkBackupReadsPersistedProtectionWithoutPrimingTheUiFlow() = runBlocking {
        val file = env.files.source()
        env.queue.beginWrite(file, requireNotNull(env.settings.config.value.target(file)))
        env.queue.close()
        val reopened = SqliteUploadQueue(RuntimeEnvironment.getApplication())
        try {
            val writer = LocalBackupWriter(reopened, env.files, env.lock, env.settings, env.wakeup)
            val runner = UploadRunner(reopened, UploadSnapshots(reopened, env.files, env.lock),
                UploadEligibility(env.settings, env.credentials, NetworkStatus { false }), TestTransport())
            val backup = BackupService(env.settings, env.credentials, reopened, writer, ExistingBackupSource { listOf(file) },
                object : RemoteFolderBrowser {
                    override suspend fun folders(session: NextcloudSession, path: RemotePath) = emptyList<RemoteFolder>()
                }, env.wakeup, runner)
            assertTrue(reopened.entries.value.isEmpty())
            backup.backupExisting()
            assertTrue(reopened.pending().isEmpty())
            assertEquals(UploadFailure.LOCAL_WRITE_INTERRUPTED, reopened.allEntries().single().failure)
        } finally { reopened.close() }
    }

    @Test fun bulkImportNeverBypassesVerificationOfFailedLocalWrites() = runBlocking {
        val file = env.files.source()
        env.queue.failWrite(env.queue.beginWrite(file, requireNotNull(env.settings.config.value.target(file))))
        service(listOf(file)).backupExisting()
        assertTrue(env.queue.pending().isEmpty())
        assertEquals(UploadFailure.LOCAL_WRITE_FAILED, env.queue.entries.value.single().failure)
    }

}
