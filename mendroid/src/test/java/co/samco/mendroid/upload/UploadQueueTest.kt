package co.samco.mendroid.upload

import android.app.Application
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
class UploadQueueTest {
    private lateinit var queue: SqliteUploadQueue
    private val file = LocalBackupFile("content://log", "phone.mend", UploadKind.LOG)
    private val target = UploadTarget(AccountId("account"), RemotePath.of("/phone.mend"))
    @Before fun setup() = runBlocking {
        RuntimeEnvironment.getApplication().deleteDatabase("nextcloud-uploads.db")
        queue = SqliteUploadQueue(RuntimeEnvironment.getApplication())
        queue.recoverAfterRestart()
    }
    @After fun teardown() { queue.close() }

    @Test fun readsDoNotRecoverRowsOrPopulateTheUiFlow() = runBlocking {
        queue.beginWrite(file, target)
        queue.enqueueExisting(file.copy(uri = "content://another"), target.copy(path = RemotePath.of("/another.mend")))
        val upload = requireNotNull(queue.claim(queue.pending().single().revision))
        queue.close()
        queue = SqliteUploadQueue(RuntimeEnvironment.getApplication())

        assertTrue(queue.pending().isEmpty())
        assertTrue(queue.isCurrent(upload))
        val persisted = queue.allEntries()
        assertEquals(setOf(UploadState.WRITING, UploadState.UPLOADING), persisted.map { it.state }.toSet())
        assertTrue(queue.entries.value.isEmpty())

        queue.refreshEntries()
        assertEquals(persisted, queue.entries.value)
        assertEquals(persisted, queue.allEntries())
    }

    @Test fun startupRecoveryRunsOnceAndDoesNotResetActiveUploadClaims() = runBlocking {
        queue.enqueueExisting(file, target)
        val upload = requireNotNull(queue.claim(queue.pending().single().revision))
        queue.close()
        queue = SqliteUploadQueue(RuntimeEnvironment.getApplication())
        queue.recoverAfterRestart()
        assertFalse(queue.isCurrent(upload))
        val active = requireNotNull(queue.claim(upload.revision))
        queue.recoverAfterRestart()
        assertTrue(queue.isCurrent(active))
        queue.recoverTransferClaims()
        assertFalse(queue.isCurrent(active))
        assertEquals(1, queue.pending().size)
    }

    @Test fun recoveryMustPrecedeNewWritesAndRepeatedCallsCannotInterruptThem() = runBlocking {
        queue.close()
        queue = SqliteUploadQueue(RuntimeEnvironment.getApplication())
        try { queue.beginWrite(file, target); fail("Expected recovery requirement") }
        catch (_: IllegalStateException) { }
        queue.recoverAfterRestart()
        val writing = queue.beginWrite(file, target)
        queue.recoverAfterRestart()
        assertEquals(UploadState.WRITING, queue.allEntries().single().state)
        assertEquals(Transition.APPLIED, queue.completeWrite(writing))
    }

    @Test fun completingOldRevisionCannotHideNewerWrite() = runBlocking {
        val first = queue.beginWrite(file, target)
        assertEquals(Transition.APPLIED, queue.completeWrite(first))
        val upload = requireNotNull(queue.claim(first.revision))
        val newest = queue.beginWrite(file, target)
        queue.completeWrite(newest)
        assertEquals(Transition.SUPERSEDED, queue.completeUpload(upload))
        assertEquals(newest.revision, queue.pending().single().revision)
        assertEquals(1, queue.entries.value.size)
    }
    @Test fun transitionChecksStateAsWellAsRevision() = runBlocking {
        val writing = queue.beginWrite(file, target)
        assertNull(queue.claim(writing.revision))
        assertEquals(Transition.APPLIED, queue.completeWrite(writing))
        assertEquals(Transition.SUPERSEDED, queue.failWrite(writing))
        val upload = requireNotNull(queue.claim(writing.revision))
        assertNull(queue.claim(writing.revision))
        queue.completeUpload(upload)
        assertEquals(Transition.SUPERSEDED, queue.failUpload(upload, UploadFailure.CONNECTION))
    }
    @Test fun restartRecoversTransfersButProtectsInterruptedWrites() = runBlocking {
        val writing = queue.beginWrite(file, target)
        val second = file.copy(uri = "content://attachment", name = "a.enc", kind = UploadKind.ATTACHMENT)
        queue.enqueueExisting(second, target.copy(path = RemotePath.of("/a.enc")))
        queue.claim(queue.pending().single().revision)
        queue.close()
        queue = SqliteUploadQueue(RuntimeEnvironment.getApplication())
        queue.recoverAfterRestart()
        queue.recoverTransferClaims()
        assertEquals(UploadFailure.LOCAL_WRITE_INTERRUPTED, queue.entries.value.first { it.revision == writing.revision }.failure)
        assertEquals(1, queue.pending().size)
    }
    @Test fun ordinaryRetryNeverQueuesFailedOrInterruptedLocalWrites() = runBlocking {
        queue.failWrite(queue.beginWrite(file, target))
        queue.enqueueExisting(file.copy(uri = "content://remote"), target.copy(path = RemotePath.of("/remote")))
        val upload = requireNotNull(queue.claim(queue.pending().single().revision))
        queue.failUpload(upload, UploadFailure.AUTHENTICATION)
        queue.retryFailures()
        assertEquals(1, queue.pending().size)
        assertEquals(UploadFailure.LOCAL_WRITE_FAILED, queue.entries.value.first { it.file == file }.failure)
    }
    @Test fun destinationsRemainBoundToOriginalAccountAndPath() = runBlocking {
        queue.enqueueExisting(file, target)
        queue.enqueueExisting(file, target.copy(account = AccountId("another"), path = RemotePath.of("/new/phone.mend")))
        assertEquals(setOf(target, target.copy(account = AccountId("another"), path = RemotePath.of("/new/phone.mend"))),
            queue.pending().map { it.target }.toSet())
    }
    @Test fun collisionsAreDetectedWithoutDiscardingEitherFile() = runBlocking {
        queue.enqueueExisting(file, target)
        queue.enqueueExisting(file.copy(uri = "content://different"), target)
        assertTrue(queue.hasCollision(queue.pending().first()))
        assertEquals(2, queue.pending().size)
    }
    @Test fun staleHistoryRemovalCannotDeleteNewerWork() = runBlocking {
        queue.enqueueExisting(file, target)
        val old = queue.pending().single()
        queue.completeUpload(requireNotNull(queue.claim(old.revision)))
        queue.enqueueExisting(file, target)
        queue.forget(old.revision)
        assertEquals(1, queue.pending().size)
    }
    @Test fun lastFailureSurvivesRetryAndRestartUntilSuccessfulUpload() = runBlocking {
        queue.enqueueExisting(file, target)
        val revision = queue.pending().single().revision
        queue.failUpload(requireNotNull(queue.claim(revision)), UploadFailure.AUTHENTICATION)
        queue.retryFailures()
        assertEquals(UploadFailure.AUTHENTICATION, queue.pending().single().failure)
        val upload = requireNotNull(queue.claim(revision))
        queue.close()
        queue = SqliteUploadQueue(RuntimeEnvironment.getApplication())
        queue.recoverAfterRestart()
        assertEquals(UploadFailure.AUTHENTICATION, queue.pending().single().failure)
        queue.completeUpload(requireNotNull(queue.claim(upload.revision)))
        assertNull(queue.entries.value.single().failure)
    }

    @Test fun transferRecoveryNeverInterferesWithAnActiveLocalWrite() = runBlocking {
        val writing = queue.beginWrite(file, target)
        queue.enqueueExisting(file.copy(uri = "content://other"), target.copy(path = RemotePath.of("/other")))
        queue.claim(queue.pending().single().revision)
        queue.recoverTransferClaims()
        assertEquals(1, queue.pending().size)
        assertEquals(UploadState.WRITING, queue.entries.value.first { it.revision == writing.revision }.state)
    }

}
