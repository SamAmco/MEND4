package co.samco.mendroid.remote

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelStore
import co.samco.mendroid.model.ErrorToastManagerImpl
import co.samco.mendroid.model.LocalLogDirectory
import co.samco.mendroid.model.LogFileManagerImpl
import co.samco.mendroid.model.LogLine
import co.samco.mendroid.model.PrivateKeyManager
import co.samco.mendroid.model.PropertyManagerImpl
import co.samco.mendroid.upload.RemotePath
import co.samco.mendroid.upload.UploadFailure
import co.samco.mendroid.viewmodel.DecryptLogItem
import co.samco.mendroid.viewmodel.SelectLogViewModel
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

private class ReadTestKeys : PrivateKeyManager {
    override val unlocked = MutableStateFlow(true)
    override val decryptingFile = MutableStateFlow(false)
    override val decryptingLog = MutableStateFlow(false)
    override val decryptedLogLines = MutableStateFlow<List<LogLine>>(emptyList())
    val received = CompletableDeferred<String>()
    override suspend fun decryptLog(inputStream: InputStream) { received.complete(inputStream.bufferedReader().readText()) }
    override suspend fun unlock(pass: CharArray) = true
    override fun onUserLockMend() { unlocked.value = false }
    override fun onScreenOff() { unlocked.value = false }
    override fun decryptEncFile(fileUri: Uri) = Unit
    override fun cancelDecryptingFile() = Unit
    override fun forceCleanFiles() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class RemoteLogViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val models = ViewModelStore()
    private lateinit var env: RemoteReadTestEnvironment
    private lateinit var logs: LogFileManagerImpl
    private lateinit var keys: ReadTestKeys
    private lateinit var folder: File

    @Before fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication()
        env = RemoteReadTestEnvironment(context, temporary.newFolder())
        folder = temporary.newFolder()
        val properties = PropertyManagerImpl(context)
        properties.setLogDirUri(folder.toURI().toString())
        logs = LogFileManagerImpl(LocalLogDirectory { DocumentFile.fromFile(folder) }, properties)
        keys = ReadTestKeys()
    }
    @After fun teardown() { models.clear(); env.cache.clear(); Dispatchers.resetMain() }
    private fun model() = SelectLogViewModel(keys, ErrorToastManagerImpl(), logs, env.files,
        RuntimeEnvironment.getApplication()).also { models.put("logs", it) }

    @Test fun unavailableRemoteFolderLeavesLocalLogsVisibleWithAnError() = runBlocking {
        File(folder, "phone.mend").writeText("local encrypted")
        env.folder("/offline")
        env.client.errors[RemotePath.of("/offline")] = IOException("offline")
        val model = model()
        val state = withTimeout(5_000) { model.state.first { !it.loading && it.logs.size == 1 } }
        assertTrue(state.logs.single() is DecryptLogItem.Local)
        assertEquals(UploadFailure.CONNECTION, state.problems.single().reason)
    }
    @Test fun remoteAndLocalLogsAreListedTogetherAndSelectionDecryptsDownloadedBytes() = runBlocking {
        File(folder, "phone.mend").writeText("local encrypted")
        env.folder("/archive")
        env.file("/archive/phone.mend", "remote encrypted")
        val model = model()
        val state = withTimeout(5_000) { model.state.first { !it.loading && it.logs.size == 2 } }
        assertEquals(2, state.logs.count { it.name == "phone.mend" })
        assertTrue(env.client.downloaded.isEmpty())
        var navigated = false
        model.onLogSelected(state.logs.filterIsInstance<DecryptLogItem.Nextcloud>().single()) { navigated = true }
        assertEquals("remote encrypted", withTimeout(5_000) { keys.received.await() })
        assertTrue(navigated)
        assertTrue(File(folder, "phone.mend").readText() == "local encrypted")
    }
    @Test fun removingASourceUpdatesTheVisibleListWithoutChangingLocalLogs() = runBlocking {
        File(folder, "phone.mend").writeText("local encrypted")
        env.folder("/archive"); env.file("/archive/another.mend")
        val model = model()
        withTimeout(5_000) { model.state.first { !it.loading && it.logs.size == 2 } }
        env.settings.removeAdditionalFolder(RemotePath.of("/archive"))
        val state = withTimeout(5_000) { model.state.first { !it.loading && it.logs.size == 1 } }
        assertTrue(state.logs.single() is DecryptLogItem.Local)
        assertTrue(File(folder, "phone.mend").exists())
    }
    @Test fun lockingClearsTheLogListAndPreventsFurtherSelections() = runBlocking {
        env.folder("/archive"); env.file("/archive/phone.mend")
        val model = model()
        val old = withTimeout(5_000) { model.state.first { !it.loading && it.logs.size == 1 } }.logs.single()
        keys.onUserLockMend()
        env.cache.clear()
        withTimeout(5_000) { model.state.first { it.logs.isEmpty() } }
        model.onLogSelected(old) { fail("Locked MEND must not navigate to decryption") }
        assertTrue(env.client.downloaded.isEmpty())
    }
}
