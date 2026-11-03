package co.samco.mendroid.model

import android.app.Application
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mendroid.AppModule
import co.samco.mendroid.remote.RemoteReadCache
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.security.PrivateKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
class PrivateKeyReadSessionTest {
    private lateinit var manager: PrivateKeyManagerImpl
    private lateinit var cache: RemoteReadCache
    private lateinit var properties: PropertyManagerImpl
    private lateinit var crypto: TestCrypto
    private val key = object : PrivateKey {
        override fun getAlgorithm() = "test"
        override fun getFormat() = "test"
        override fun getEncoded() = byteArrayOf(1)
    }
    private inner class TestCrypto(delegate: CryptoProvider) : CryptoProvider by delegate {
        var unlockHook: (() -> Unit)? = null
        var decryptHook: (suspend () -> Unit)? = null
        override fun unlock(password: CharArray): UnlockResult { unlockHook?.invoke(); return UnlockResult.Success(key) }
        override suspend fun decryptLogStream(privateKey: PrivateKey, inputStream: InputStream, outputStream: PrintStream) {
            decryptHook?.invoke()
            outputStream.print("plain log contents")
        }
    }
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        properties = PropertyManagerImpl(context)
        cache = RemoteReadCache(File(context.cacheDir, "session-test"))
        crypto = TestCrypto(AppModule().provideCryptoProvider(properties))
        manager = PrivateKeyManagerImpl(crypto, ErrorToastManagerImpl(), properties, context, cache)
    }
    @After fun teardown() { manager.onUserLockMend(); manager.coroutineContext[Job]?.cancel() }

    @Test fun manualLockClearsDownloadedFilesAndUnlockStateImmediately() = runBlocking {
        assertTrue(manager.unlock(charArrayOf()))
        assertTrue(manager.unlocked.value)
        val file = cache.withSession { lease -> cache.download(lease) { it.write(1) } }
        manager.onUserLockMend()
        assertFalse(manager.unlocked.value)
        assertFalse(file.exists())
    }
    @Test fun screenOffClearsDownloadsOnlyWhenScreenLockIsEnabled() = runBlocking {
        manager.unlock(charArrayOf())
        val file = cache.withSession { lease -> cache.download(lease) { it.write(1) } }
        properties.setLockOnScreenLock(false)
        manager.onScreenOff()
        assertTrue(manager.unlocked.value)
        assertTrue(file.exists())
        properties.setLockOnScreenLock(true)
        manager.onScreenOff()
        assertFalse(manager.unlocked.value)
        assertFalse(file.exists())
    }
    @Test fun logDecryptionFinishingAfterLockCannotRestorePlaintextState() = runBlocking {
        manager.unlock(charArrayOf())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        crypto.decryptHook = { withContext(NonCancellable) { entered.complete(Unit); release.await() } }
        val decrypting = async { manager.decryptLog(byteArrayOf(1).inputStream()) }
        entered.await()
        manager.onUserLockMend()
        release.complete(Unit)
        decrypting.await()
        assertTrue(manager.decryptedLogLines.value.isEmpty())
        assertFalse(manager.decryptingLog.value)
    }
    @Test fun lockingWhilePasswordCheckIsRunningPreventsALateUnlock() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        crypto.unlockHook = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val unlocking = async(Dispatchers.IO) { manager.unlock(charArrayOf()) }
        withContext(Dispatchers.IO) { check(entered.await(5, TimeUnit.SECONDS)) }
        manager.onUserLockMend()
        release.countDown()
        assertFalse(unlocking.await())
        assertFalse(manager.unlocked.value)
    }
}
