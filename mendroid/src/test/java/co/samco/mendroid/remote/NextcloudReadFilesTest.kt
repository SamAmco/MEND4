package co.samco.mendroid.remote

import android.app.Application
import android.net.Uri
import co.samco.mendroid.nextcloud.NextcloudAccount
import co.samco.mendroid.nextcloud.NextcloudException
import co.samco.mendroid.nextcloud.NextcloudFailure
import co.samco.mendroid.nextcloud.NextcloudSession
import co.samco.mendroid.nextcloud.RemotePath
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
class NextcloudReadFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var env: RemoteReadTestEnvironment
    @Before fun setup() { env = RemoteReadTestEnvironment(RuntimeEnvironment.getApplication(), temporary.newFolder()) }
    @After fun teardown() { env.cache.clear() }

    @Test fun listsOnlyDirectMendFilesAndRetainsDuplicateNamesFromDifferentFolders() = runBlocking {
        env.folder("/a"); env.folder("/b")
        env.file("/a/same.mend"); env.file("/b/same.mend"); env.file("/a/image.enc")
        env.client.listing[RemotePath.of("/a")] = env.client.listing.getValue(RemotePath.of("/a")) + listOf(
            RemoteDirectoryEntry(RemotePath.of("/a/sub"), true),
            RemoteDirectoryEntry(RemotePath.of("/a/sub/hidden.mend"), false))
        val result = env.files.logs()
        assertEquals(listOf("/a/same.mend", "/b/same.mend"), result.files.map { it.path.value })
        assertEquals(listOf("/a", "/b"), env.client.listed.map { it.value })
        assertTrue(env.client.downloaded.isEmpty())
    }
    @Test fun failedFolderDoesNotHideLogsFromOtherFolders() = runBlocking {
        env.folder("/a"); env.folder("/b")
        env.client.errors[RemotePath.of("/a")] = IOException("offline")
        val available = env.file("/b/available.mend")
        val result = env.files.logs()
        assertEquals(listOf(available), result.files)
        assertEquals(listOf(RemoteReadProblem(RemotePath.of("/a"), NextcloudFailure.CONNECTION)), result.problems)
    }
    @Test fun logIsDownloadedOnSelectionAndFetchedFreshOnEachOpen() = runBlocking {
        env.folder("/logs")
        val file = env.file("/logs/phone.mend", "first encrypted version")
        assertEquals("first encrypted version", env.files.open(file).readText())
        env.client.contents[file.path] = "new encrypted version"
        assertEquals("new encrypted version", env.files.open(file).readText())
        assertEquals(listOf(file.path, file.path), env.client.downloaded)
    }
    @Test fun onlyConfiguredDirectChildrenCanBeDownloaded() = runBlocking {
        env.folder("/logs")
        for (path in listOf("/other/a.mend", "/logs/sub/a.mend")) {
            try { env.files.open(RemoteReadFile(env.session.account.id, RemotePath.of(path))); fail() }
            catch (e: NextcloudException) { assertEquals(NextcloudFailure.DESTINATION_MISSING, e.reason) }
        }
        assertTrue(env.client.downloaded.isEmpty())
    }
    @Test fun accountChangeCannotDownloadPreviouslyListedFiles() = runBlocking {
        env.folder("/logs")
        val file = env.file("/logs/phone.mend")
        env.settings.connect(NextcloudSession(NextcloudAccount("https://cloud.example", "other"), "other", "other-secret"))
        try { env.files.open(file); fail() }
        catch (e: NextcloudException) { assertEquals(NextcloudFailure.ACCOUNT_UNAVAILABLE, e.reason) }
        assertTrue(env.client.downloaded.isEmpty())
    }
    @Test fun localAttachmentTakesPrecedenceWithoutContactingNextcloud() = runBlocking {
        env.folder("/enc")
        env.file("/enc/id.enc")
        val local = Uri.parse("content://local/id.enc")
        assertEquals(local, AttachmentFiles({ local }, env.files).find("id.enc"))
        assertTrue(env.client.listed.isEmpty())
        assertTrue(env.client.downloaded.isEmpty())
    }
    @Test fun missingLocalAttachmentIsReadFromTheAdditionalFolder() = runBlocking {
        env.folder("/enc")
        val file = env.file("/enc/id.enc", "encrypted attachment")
        val uri = requireNotNull(AttachmentFiles({ null }, env.files).find("id.enc"))
        assertEquals("encrypted attachment", File(requireNotNull(uri.path)).readText())
        assertEquals(listOf(file.path), env.client.downloaded)
    }
    @Test fun lockingCancelsTheDownloadAndLateCompletionCannotRecreateTheCache() = runBlocking {
        env.folder("/logs")
        val file = env.file("/logs/phone.mend")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        env.client.downloadHook = { withContext(NonCancellable) { entered.complete(Unit); release.await() } }
        val opening = async { env.files.open(file) }
        withTimeout(5_000) { entered.await() }
        env.cache.clear()
        env.cache.openSession()
        release.complete(Unit)
        try { opening.await(); fail() } catch (_: CancellationException) { }
        assertFalse(env.directory.exists())
    }
    @Test fun removingFolderDuringDownloadDiscardsTheResult() = runBlocking {
        env.folder("/logs")
        val file = env.file("/logs/phone.mend")
        env.client.downloadHook = { env.settings.removeAdditionalFolder(RemotePath.of("/logs")) }
        try { env.files.open(file); fail() }
        catch (e: NextcloudException) { assertEquals(NextcloudFailure.DESTINATION_MISSING, e.reason) }
        assertTrue(env.directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun lockedSessionCannotIssueReadRequests() = runBlocking {
        env.folder("/logs")
        env.cache.clear()
        try { env.files.logs(); fail() } catch (_: CancellationException) { }
        assertTrue(env.client.listed.isEmpty())
    }
}
