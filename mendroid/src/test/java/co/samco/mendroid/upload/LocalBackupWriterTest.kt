package co.samco.mendroid.upload

import android.app.Application
import androidx.core.content.edit
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
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
class LocalBackupWriterTest {
    private lateinit var env: UploadTestEnvironment
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("nextcloud-uploads.db")
        context.getSharedPreferences("nextcloud", 0).edit { clear() }
        env = UploadTestEnvironment(context)
        env.connect()
    }
    @After fun teardown() { env.queue.close() }

    @Test fun writerClosesStreamBeforeQueuingAndWakingUploader() = runBlocking {
        val file = env.files.source()
        val result = env.writer.write(file, WriteMode.REPLACE) { it.write("complete".toByteArray()) }
        assertTrue(env.files.outputClosed)
        assertEquals(BackupRegistration.QUEUED, result.backup)
        assertEquals(1, env.queue.pending().size)
        assertEquals(1, env.wakeup.requests)
    }
    @Test fun successfulWriteWaitsForSchedulingConfirmation() = runBlocking {
        val scheduling = CompletableDeferred<Unit>()
        val confirmed = CompletableDeferred<Unit>()
        env.wakeup.onRequest = { scheduling.complete(Unit); confirmed.await() }
        val file = env.files.source()
        val writing = async { env.writer.write(file, WriteMode.REPLACE) { it.write("saved".toByteArray()) } }
        scheduling.await()
        assertTrue(env.files.outputClosed)
        assertEquals(1, env.queue.pending().size)
        assertFalse(writing.isCompleted)
        confirmed.complete(Unit)
        assertEquals(BackupRegistration.QUEUED, writing.await().backup)
    }

    @Test fun cancellationAfterSuccessfulClosureStillFinishesScheduling() = runBlocking {
        val scheduling = CompletableDeferred<Unit>()
        val confirmed = CompletableDeferred<Unit>()
        env.wakeup.onRequest = { scheduling.complete(Unit); confirmed.await() }
        val file = env.files.source()
        val writing = async { env.writer.write(file, WriteMode.REPLACE) { it.write("saved".toByteArray()) } }
        scheduling.await()
        writing.cancel()
        assertFalse(writing.isCompleted)
        confirmed.complete(Unit)
        writing.join()
        assertEquals("saved", env.files.sources.getValue(file.uri).readText())
        assertEquals(1, env.queue.pending().size)
        assertEquals(1, env.wakeup.requests)
    }

    @Test fun schedulingFailurePreservesTheLocalSaveAndPendingEntryForStartupRecovery() = runBlocking {
        env.wakeup.onRequest = { throw IOException("WorkManager unavailable") }
        val file = env.files.source()
        val saved = env.writer.write(file, WriteMode.REPLACE) { it.write("saved".toByteArray()) }
        assertEquals(BackupRegistration.QUEUED, saved.backup)
        assertEquals("saved", env.files.sources.getValue(file.uri).readText())
        assertEquals(1, env.queue.pending().size)
    }

    @Test fun beginQueueFailureStillSavesLocalEncryptedFile() = runBlocking {
        val broken = object : UploadQueue by env.queue {
            override suspend fun beginWrite(file: LocalBackupFile, target: UploadTarget): WriteToken = throw IOException("queue unavailable")
        }
        val writer = LocalBackupWriter(broken, env.files, env.lock, env.settings, env.wakeup)
        val file = env.files.source()
        val saved = writer.write(file, WriteMode.REPLACE) { it.write("saved".toByteArray()) }
        assertEquals("saved", env.files.sources.getValue(file.uri).readText())
        assertEquals(BackupRegistration.NEEDS_ATTENTION, saved.backup)
        assertTrue(env.settings.config.value.needsRescan)
    }
    @Test fun queueCompletionFailureIsSeparateFromSuccessfulLocalSave() = runBlocking {
        val broken = object : UploadQueue by env.queue {
            override suspend fun completeWrite(token: WriteToken): Transition = throw IOException("queue unavailable")
        }
        val file = env.files.source()
        val writer = LocalBackupWriter(broken, env.files, env.lock, env.settings, env.wakeup)
        assertEquals(BackupRegistration.NEEDS_ATTENTION, writer.write(file, WriteMode.REPLACE) { it.write(1) }.backup)
        assertTrue(env.files.outputClosed)
        assertTrue(env.queue.pending().isEmpty())
        assertEquals(UploadFailure.REGISTRATION_FAILED, env.queue.entries.value.single().failure)
        env.queue.retryFailures()
        assertEquals(1, env.queue.pending().size)
    }
    @Test fun localWriteFailureIsProtectedFromRetry() = runBlocking {
        val file = env.files.source()
        try { env.writer.write(file, WriteMode.REPLACE) { throw IOException("write failed") }; fail() }
        catch (_: IOException) { }
        env.queue.retryFailures()
        assertTrue(env.queue.pending().isEmpty())
        assertEquals(UploadFailure.LOCAL_WRITE_FAILED, env.queue.entries.value.single().failure)
    }
    @Test fun existingFilesUseExplicitQueueOperation() = runBlocking {
        val file = env.files.source()
        assertEquals(BackupRegistration.QUEUED, env.writer.enqueueExisting(file))
        assertFalse(env.files.outputClosed)
        assertEquals(UploadState.PENDING, env.queue.entries.value.single().state)
    }
    @Test fun snapshotsWaitForWritesAndStayStableAfterNewWrites() = runBlocking {
        val file = env.files.source()
        env.writer.enqueueExisting(file)
        val old = env.queue.pending().single()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val writing = async {
            env.writer.write(file, WriteMode.REPLACE) { output ->
                output.write("half".toByteArray())
                started.complete(Unit)
                finish.await()
                output.write("complete".toByteArray())
            }
        }
        started.await()
        val preparing = async { env.snapshots.prepare(old) }
        finish.complete(Unit)
        writing.await()
        assertEquals(SnapshotResult.Superseded, preparing.await())
        val snapshot = (env.snapshots.prepare(env.queue.pending().single()) as SnapshotResult.Ready).snapshot
        env.writer.write(file, WriteMode.REPLACE) { it.write("newer".toByteArray()) }
        assertEquals("halfcomplete", snapshot.file.readText())
        snapshot.close()
        assertFalse(snapshot.file.exists())
    }
    @Test fun missingFileProducesExplicitUnavailableResult() = runBlocking {
        val file = env.files.source()
        env.writer.enqueueExisting(file)
        env.files.sources.remove(file.uri)
        assertEquals(SnapshotResult.Unavailable, env.snapshots.prepare(env.queue.pending().single()))
        assertEquals(UploadFailure.LOCAL_FILE_UNAVAILABLE, env.queue.entries.value.single().failure)
    }
    @Test fun localOnlyModeDoesNotDependOnQueue() = runBlocking {
        env.settings.disconnect()
        val file = env.files.source()
        assertEquals(BackupRegistration.NOT_CONFIGURED, env.writer.write(file, WriteMode.REPLACE) { it.write(2) }.backup)
        assertTrue(env.queue.entries.value.isEmpty())
    }
    @Test fun cancelledLocalWriteCannotBeEnabledByOrdinaryRetry() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val writing = async {
            env.writer.write(env.files.source(), WriteMode.REPLACE) { output ->
                output.write("partial".toByteArray())
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        started.await()
        writing.cancelAndJoin()
        env.queue.retryFailures()
        assertTrue(env.queue.pending().isEmpty())
        assertEquals(UploadFailure.LOCAL_WRITE_FAILED, env.queue.entries.value.single().failure)
    }

}
