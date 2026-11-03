package co.samco.mendroid.remote

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteReadCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun directory() = File(temporary.root, "downloads")

    @Test fun successfulDownloadRemainsUntilLock() = runBlocking {
        val cache = RemoteReadCache(directory())
        cache.openSession()
        val file = cache.withSession { lease -> cache.download(lease) { it.write("encrypted".toByteArray()) } }
        assertEquals("encrypted", file.readText())
        cache.clear()
        assertFalse(file.exists())
        assertFalse(directory().exists())
    }
    @Test fun failedDownloadDeletesItsPartialFile() = runBlocking {
        val cache = RemoteReadCache(directory())
        cache.openSession()
        try {
            cache.withSession { lease -> cache.download(lease) { it.write(1); throw IOException("offline") } }
            fail()
        } catch (_: IOException) { }
        assertTrue(directory().listFiles().orEmpty().isEmpty())
        cache.clear()
    }
    @Test fun cancelledDownloadDeletesItsPartialFile() = runBlocking {
        val cache = RemoteReadCache(directory())
        cache.openSession()
        val entered = CompletableDeferred<Unit>()
        val download = async {
            cache.withSession { lease -> cache.download(lease) { it.write(1); entered.complete(Unit); CompletableDeferred<Unit>().await() } }
        }
        entered.await()
        download.cancel()
        download.join()
        assertTrue(directory().listFiles().orEmpty().isEmpty())
        cache.clear()
    }
    @Test fun oldSessionCannotPublishAfterLockEvenIfTheWriterIgnoresCancellation() = runBlocking {
        val cache = RemoteReadCache(directory())
        cache.openSession()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val download = async {
            cache.withSession { lease -> cache.download(lease) { output ->
                withContext(NonCancellable) { entered.complete(Unit); finish.await(); output.write(1) }
            } }
        }
        entered.await()
        cache.clear()
        cache.openSession()
        finish.complete(Unit)
        try { download.await(); fail() } catch (_: CancellationException) { }
        assertFalse(directory().exists())
        cache.clear()
    }
    @Test fun expiredLeaseCannotStartAnotherDownloadAfterUnlockingAgain() = runBlocking {
        val cache = RemoteReadCache(directory())
        cache.openSession()
        val old = cache.withSession { it }
        cache.clear()
        cache.openSession()
        try { cache.download(old) { it.write(1) }; fail() } catch (_: CancellationException) { }
        assertFalse(directory().exists())
        cache.clear()
    }
}
