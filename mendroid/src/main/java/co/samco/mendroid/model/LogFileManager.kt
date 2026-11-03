package co.samco.mendroid.model

import android.net.Uri
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LogFileData(val name: String, val uri: Uri)

interface LogFileManager {
    val currentLog: Flow<LogFileData?>
    val logs: Flow<List<LogFileData>>
    suspend fun refresh()
    suspend fun currentForWrite(): LogFileData?
    suspend fun select(log: LogFileData)
    suspend fun create(name: String)
}

/** Directory snapshots and selection only; no registry of individually imported documents. */
class LogFileManagerImpl @Inject constructor(
    private val directory: LocalLogDirectory,
    private val properties: PropertyManager
) : LogFileManager {
    private data class Selection(val logs: List<LogFileData> = emptyList(), val current: LogFileData? = null)
    private val selection = MutableStateFlow(Selection())
    private val lock = Mutex()
    override val currentLog = selection.map { it.current }.distinctUntilChanged()
    override val logs = selection.map { it.logs }.distinctUntilChanged()

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        lock.withLock { refreshSelection() }
    }

    override suspend fun currentForWrite(): LogFileData? = withContext(Dispatchers.IO) {
        lock.withLock {
            refreshSelection()
            selection.value.current
        }
    }

    override suspend fun select(log: LogFileData) = withContext(Dispatchers.IO) {
        lock.withLock {
            refreshSelection()
            val current = selection.value.logs.firstOrNull { it.uri == log.uri }
                ?: throw IOException("Selected log is no longer in the directory")
            properties.setCurrentLogUri(current.uri.toString())
            selection.value = selection.value.copy(current = current)
        }
    }

    override suspend fun create(name: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val log = directory.create(name)
            properties.setCurrentLogUri(log.uri.toString())
            refreshSelection()
        }
    }

    private suspend fun refreshSelection() {
        if (properties.getLogDirUri() == null) {
            selection.value = Selection()
            return
        }
        try {
            val logs = directory.files()
            val current = logs.firstOrNull { it.uri.toString() == properties.getCurrentLogUri() }
            if (current == null) properties.setCurrentLogUri(null)
            selection.value = Selection(logs, current)
        } catch (e: IOException) {
            selection.value = Selection()
            throw e
        } catch (e: SecurityException) {
            selection.value = Selection()
            throw e
        }
    }
}
