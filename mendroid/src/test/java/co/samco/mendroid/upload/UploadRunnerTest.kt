package co.samco.mendroid.upload

import android.app.Application
import co.samco.mendroid.nextcloud.AccountId
import co.samco.mendroid.nextcloud.NextcloudException
import co.samco.mendroid.nextcloud.NextcloudFailure
import co.samco.mendroid.nextcloud.NextcloudSession
import co.samco.mendroid.nextcloud.RemotePath
import java.io.File
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

internal class TestTransport : UploadTransport {
    val staged = mutableListOf<String>()
    val published = mutableListOf<String>()
    var discarded = 0
    var stageHook: (suspend () -> Unit)? = null
    var failure: NextcloudFailure? = null
    override suspend fun stage(session: NextcloudSession, path: RemotePath, file: File, progress: (Long) -> Unit): StagedUpload {
        val contents = file.readText()
        staged.add(contents)
        stageHook?.invoke()
        failure?.let { throw NextcloudException(it) }
        return object : StagedUpload {
            override suspend fun publish(policy: OverwritePolicy) { published.add(contents) }
            override suspend fun discard() { discarded++ }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class UploadRunnerTest {
    private lateinit var env: UploadTestEnvironment
    private lateinit var transport: TestTransport
    private lateinit var runner: UploadRunner
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("nextcloud-uploads.db")
        env = UploadTestEnvironment(context)
        env.connect()
        transport = TestTransport()
        runner = UploadRunner(env.queue, env.snapshots, UploadEligibility(env.settings, env.credentials, NetworkStatus { false }), transport)
    }
    @After fun teardown() { env.queue.close() }

    @Test fun newerLogRevisionSkipsObsoletePublicationAndWinsInSameRun() = runBlocking {
        val file = env.files.source(content = "first")
        env.writer.enqueueExisting(file)
        transport.stageHook = {
            transport.stageHook = null
            env.writer.write(file, WriteMode.REPLACE) { it.write("latest".toByteArray()) }
        }
        assertEquals(RunResult.COMPLETE, runner.run())
        assertEquals(listOf("first", "latest"), transport.staged)
        assertEquals(listOf("latest"), transport.published)
        assertEquals(2, transport.discarded)
        assertTrue(env.files.snapshots.all { !it.exists() })
        assertEquals(UploadState.UPLOADED, env.queue.entries.value.single().state)
    }
    @Test fun runnerSerializesEvenWhenCalledOutsideWorkManager() = runBlocking {
        val file = env.files.source()
        env.writer.enqueueExisting(file)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        transport.stageHook = { entered.complete(Unit); release.await() }
        val first = async { runner.run() }
        entered.await()
        val second = async { runner.run() }
        assertEquals(1, transport.staged.size)
        release.complete(Unit)
        first.await(); second.await()
        assertEquals(1, transport.published.size)
    }
    @Test fun otherAccountNeverReceivesAnOldQueuedFile() = runBlocking {
        val file = env.files.source()
        env.queue.enqueueExisting(file, UploadTarget(AccountId("another"), RemotePath.of("/a")))
        runner.run()
        assertTrue(transport.staged.isEmpty())
        assertEquals(UploadFailure.ACCOUNT_UNAVAILABLE, env.queue.entries.value.single().failure)
    }
    @Test fun destinationCollisionIsReportedBeforeSnapshotOrTransfer() = runBlocking {
        env.writer.enqueueExisting(env.files.source())
        env.writer.enqueueExisting(env.files.source())
        runner.run()
        assertTrue(transport.staged.isEmpty())
        assertTrue(env.files.snapshots.isEmpty())
        assertTrue(env.queue.entries.value.all { it.failure == UploadFailure.DESTINATION_COLLISION })
    }
    @Test fun transientFailureReturnsRetryWithoutDeletingLocalFile() = runBlocking {
        val file = env.files.source(content = "saved")
        env.writer.enqueueExisting(file)
        transport.failure = NextcloudFailure.SERVER_BUSY
        assertEquals(RunResult.RETRY, runner.run())
        assertEquals("saved", env.files.sources.getValue(file.uri).readText())
        assertEquals(1, env.queue.pending().size)
    }
    @Test fun pauseAndMeteredPolicyWaitWithoutSendingAnything() = runBlocking {
        env.writer.enqueueExisting(env.files.source())
        env.settings.pause(true)
        assertEquals(RunResult.WAITING, runner.run())
        env.settings.pause(false)
        val metered = UploadRunner(env.queue, env.snapshots, UploadEligibility(env.settings, env.credentials, NetworkStatus { true }), transport)
        assertEquals(RunResult.WAITING, metered.run())
        assertTrue(transport.staged.isEmpty())
    }
    @Test fun pauseAfterStagingDoesNotPublish() = runBlocking {
        env.writer.enqueueExisting(env.files.source())
        transport.stageHook = { env.settings.pause(true) }
        assertEquals(RunResult.WAITING, runner.run())
        assertTrue(transport.published.isEmpty())
        assertEquals(1, transport.discarded)
        assertEquals(1, env.queue.pending().size)
    }
    @Test fun cancellationReturnsClaimToQueueAndCleansSnapshot() = runBlocking {
        env.writer.enqueueExisting(env.files.source())
        val entered = CompletableDeferred<Unit>()
        transport.stageHook = { entered.complete(Unit); CompletableDeferred<Unit>().await() }
        val uploading = async { runner.run() }
        entered.await()
        uploading.cancelAndJoin()
        assertEquals(1, env.queue.pending().size)
        assertTrue(env.files.snapshots.all { !it.exists() })
    }
    @Test fun nextRunRecoversClaimAfterDatabaseFailureWithoutProcessRestart() = runBlocking {
        env.writer.enqueueExisting(env.files.source())
        var unavailable = true
        val interrupted = object : UploadQueue by env.queue {
            override suspend fun completeUpload(token: UploadToken): Transition {
                if (unavailable) throw IOException("database unavailable")
                return env.queue.completeUpload(token)
            }
            override suspend fun failUpload(token: UploadToken, failure: UploadFailure): Transition {
                if (unavailable) throw IOException("database unavailable")
                return env.queue.failUpload(token, failure)
            }
        }
        val recovering = UploadRunner(interrupted, UploadSnapshots(interrupted, env.files, env.lock),
            UploadEligibility(env.settings, env.credentials, NetworkStatus { false }), transport)
        try { recovering.run(); fail() } catch (_: IOException) { }
        assertEquals(UploadState.UPLOADING, env.queue.entries.value.single().state)
        unavailable = false
        assertEquals(RunResult.COMPLETE, recovering.run())
        assertEquals(UploadState.UPLOADED, env.queue.entries.value.single().state)
    }

    @Test fun anotherDestinationsFailedWriteBlocksSnapshotUntilVerified() = runBlocking {
        val file = env.files.source(content = "encrypted bytes")
        val original = requireNotNull(env.settings.config.value.target(file))
        env.writer.enqueueExisting(file)
        env.settings.selectLogFolder(RemotePath.of("/new"))
        val changed = requireNotNull(env.settings.config.value.target(file))
        env.queue.failWrite(env.queue.beginWrite(file, changed))
        val failure = env.queue.allEntries().single { it.target == changed }
        assertEquals(RunResult.COMPLETE, runner.run())
        assertTrue(transport.staged.isEmpty())
        assertEquals(UploadState.PENDING, env.queue.allEntries().single { it.target == original }.state)
        assertEquals(BackupRegistration.QUEUED,
            env.writer.enqueueExisting(file, changed, verifiedRevision = failure.revision))
        runner.run()
        assertEquals(2, transport.published.size)
        assertTrue(env.queue.allEntries().all { it.state == UploadState.UPLOADED })
    }

}
