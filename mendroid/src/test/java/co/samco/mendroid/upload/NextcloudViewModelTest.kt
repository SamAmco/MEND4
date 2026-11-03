package co.samco.mendroid.upload

import android.app.Application
import androidx.lifecycle.ViewModelStore
import co.samco.mendroid.nextcloud.LoginRequest
import co.samco.mendroid.nextcloud.NextcloudLogin
import co.samco.mendroid.nextcloud.NextcloudSession
import co.samco.mendroid.nextcloud.RemoteFolder
import co.samco.mendroid.nextcloud.RemoteFolderBrowser
import co.samco.mendroid.nextcloud.RemotePath
import co.samco.mendroid.viewmodel.NextcloudFolderUse
import co.samco.mendroid.viewmodel.NextcloudViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NextcloudViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var env: UploadTestEnvironment
    private lateinit var runner: UploadRunner
    private val models = ViewModelStore()
    private val login = object : NextcloudLogin {
        override suspend fun begin(server: String) = LoginRequest("https://cloud.example/login", "https://cloud.example/poll", "secret")
        override suspend fun awaitSession(login: LoginRequest) = env.session
    }
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("nextcloud-uploads.db")
        env = UploadTestEnvironment(context)
        env.connect()
        runner = UploadRunner(env.queue, env.snapshots, UploadEligibility(env.settings, env.credentials, NetworkStatus { false }), TestTransport())
    }
    @After fun teardown() { models.clear(); env.queue.close(); Dispatchers.resetMain() }
    private fun model(browser: RemoteFolderBrowser): NextcloudViewModel {
        val service = BackupService(env.settings, env.credentials, env.queue, env.writer,
            ExistingBackupSource { emptyList() }, browser, env.wakeup, runner)
        return NextcloudViewModel(service, login).also { models.put("test", it) }
    }

    @Test fun dismissedPickerCannotReopenWhenAnOldRequestCompletes() = runTest(dispatcher) {
        val response = CompletableDeferred<List<RemoteFolder>>()
        val model = model(object : RemoteFolderBrowser {
            override suspend fun folders(session: NextcloudSession, path: RemotePath) = withContext(NonCancellable) { response.await() }
        })
        model.browse(NextcloudFolderUse.LOGS)
        runCurrent()
        model.closeFolderPicker()
        response.complete(listOf(RemoteFolder("old", RemotePath.of("/old"))))
        advanceUntilIdle()
        assertNull(model.state.value.actions.picker)
    }
    @Test fun lateResponseCannotReplaceMoreRecentNavigation() = runTest(dispatcher) {
        val old = CompletableDeferred<List<RemoteFolder>>()
        val model = model(object : RemoteFolderBrowser {
            override suspend fun folders(session: NextcloudSession, path: RemotePath): List<RemoteFolder> =
                if (path == RemotePath.ROOT) withContext(NonCancellable) { old.await() } else emptyList()
        })
        model.browse(NextcloudFolderUse.LOGS)
        runCurrent()
        model.browse(NextcloudFolderUse.LOGS, RemotePath.of("/new"))
        runCurrent()
        old.complete(listOf(RemoteFolder("stale", RemotePath.of("/stale"))))
        advanceUntilIdle()
        assertEquals(RemotePath.of("/new"), model.state.value.actions.picker?.path)
        assertTrue(model.state.value.actions.picker?.folders.orEmpty().isEmpty())
    }
    @Test fun reconnectingDoesNotQueueFailedLocalWrite() = runTest(dispatcher) {
        env.queue.failWrite(env.queue.beginWrite(env.files.source(), requireNotNull(env.settings.config.value.target(env.files.source()))))
        val model = model(object : RemoteFolderBrowser {
            override suspend fun folders(session: NextcloudSession, path: RemotePath) = emptyList<RemoteFolder>()
        })
        model.connect("https://cloud.example")
        advanceUntilIdle()
        assertTrue(env.queue.pending().isEmpty())
        assertTrue(env.queue.entries.value.single().failure?.requiresLocalVerification == true)
    }
}
