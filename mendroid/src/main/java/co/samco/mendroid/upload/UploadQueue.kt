package co.samco.mendroid.upload

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface UploadQueue {
    val entries: StateFlow<List<UploadEntry>>
    suspend fun pending(): List<UploadEntry>
    suspend fun recoverTransferClaims()
    suspend fun beginWrite(file: LocalBackupFile, target: UploadTarget): WriteToken
    suspend fun completeWrite(token: WriteToken): Transition
    suspend fun failWrite(token: WriteToken): Transition
    suspend fun registrationFailed(token: WriteToken): Transition
    suspend fun enqueueExisting(file: LocalBackupFile, target: UploadTarget)
    suspend fun claim(revision: UploadRevision): UploadToken?
    suspend fun isCurrent(token: UploadToken): Boolean
    suspend fun hasCollision(entry: UploadEntry): Boolean
    suspend fun completeUpload(token: UploadToken): Transition
    suspend fun failUpload(token: UploadToken, failure: UploadFailure): Transition
    suspend fun deferUpload(token: UploadToken): Transition
    suspend fun retryFailures()
    suspend fun forget(revision: UploadRevision)
}

/** Every transition is transactional and checks both revision and expected state. */
@Singleton
class SqliteUploadQueue @Inject constructor(@ApplicationContext context: Context) :
    SQLiteOpenHelper(context, "nextcloud-uploads.db", null, 1), UploadQueue {
    private val lock = Any()
    private var initialized = false
    private val mutableEntries = MutableStateFlow<List<UploadEntry>>(emptyList())
    override val entries = mutableEntries.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE uploads (
            id INTEGER PRIMARY KEY, source TEXT NOT NULL, name TEXT NOT NULL,
            account TEXT NOT NULL, remote TEXT NOT NULL, kind TEXT NOT NULL,
            revision INTEGER NOT NULL, state TEXT NOT NULL, failure TEXT,
            UNIQUE(account, remote, source))""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException("Missing upload database migration from $oldVersion to $newVersion")
    }

    private suspend fun <T> access(publish: Boolean = true, block: SQLiteDatabase.() -> T): T = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                if (!initialized) {
                    db.execSQL("UPDATE uploads SET state = 'PENDING' WHERE state = 'UPLOADING'")
                    db.execSQL("""UPDATE uploads SET state = 'ATTENTION', failure = 'LOCAL_WRITE_INTERRUPTED'
                        WHERE state = 'WRITING'""")
                }
                val result = db.block()
                val latest = if (publish || !initialized) db.select() else null
                db.setTransactionSuccessful()
                db.endTransaction()
                initialized = true
                if (latest != null) mutableEntries.value = latest
                result
            } finally {
                if (db.inTransaction()) db.endTransaction()
            }
        }
    }

    private fun SQLiteDatabase.select(where: String? = null, args: Array<String>? = null): List<UploadEntry> =
        query("uploads", null, where, args, null, null, "id DESC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.entry()) }
        }

    private fun Cursor.entry(): UploadEntry {
        fun string(column: String) = getString(getColumnIndexOrThrow(column))
        return UploadEntry(
            UploadRevision(getLong(getColumnIndexOrThrow("id")), getLong(getColumnIndexOrThrow("revision"))),
            LocalBackupFile(string("source"), string("name"), UploadKind.valueOf(string("kind"))),
            UploadTarget(AccountId(string("account")), RemotePath.of(string("remote"))),
            UploadState.valueOf(string("state")), string("failure")?.let(UploadFailure::valueOf)
        )
    }

    private fun SQLiteDatabase.newRevision(file: LocalBackupFile, target: UploadTarget, state: UploadState): UploadRevision {
        val previous = select("source = ? AND account = ? AND remote = ?",
            arrayOf(file.uri, target.account.value, target.path.value)).singleOrNull()
        val number = (previous?.revision?.number ?: 0) + 1
        val values = ContentValues().apply {
            put("source", file.uri); put("name", file.name); put("kind", file.kind.name)
            put("account", target.account.value); put("remote", target.path.value)
            put("revision", number); put("state", state.name); putNull("failure")
        }
        val id = if (previous == null) insertOrThrow("uploads", null, values) else {
            update("uploads", values, "id = ?", arrayOf(previous.revision.id.toString()))
            previous.revision.id
        }
        return UploadRevision(id, number)
    }

    private fun SQLiteDatabase.transition(
        revision: UploadRevision, expected: UploadState, next: UploadState, failure: UploadFailure? = null
    ): Transition {
        val count = update("uploads", ContentValues().apply {
            put("state", next.name)
            // Keep the last failure visible through retries; success or a new revision clears it.
            if (failure != null) put("failure", failure.name)
            else if (next == UploadState.UPLOADED) putNull("failure")
        }, "id = ? AND revision = ? AND state = ?",
            arrayOf(revision.id.toString(), revision.number.toString(), expected.name))
        return if (count == 1) Transition.APPLIED else Transition.SUPERSEDED
    }

    override suspend fun pending() = access(publish = false) { select("state = 'PENDING'") }
    // Called only after the runner acquires exclusive transfer ownership. Local WRITING rows are untouched.
    override suspend fun recoverTransferClaims() {
        access { execSQL("UPDATE uploads SET state = 'PENDING' WHERE state = 'UPLOADING'") }
    }
    override suspend fun beginWrite(file: LocalBackupFile, target: UploadTarget) =
        access { WriteToken(newRevision(file, target, UploadState.WRITING)) }
    override suspend fun completeWrite(token: WriteToken) = access {
        transition(token.revision, UploadState.WRITING, UploadState.PENDING)
    }
    override suspend fun failWrite(token: WriteToken) = access {
        transition(token.revision, UploadState.WRITING, UploadState.ATTENTION, UploadFailure.LOCAL_WRITE_FAILED)
    }
    override suspend fun registrationFailed(token: WriteToken) = access {
        transition(token.revision, UploadState.WRITING, UploadState.ATTENTION, UploadFailure.REGISTRATION_FAILED)
    }
    override suspend fun enqueueExisting(file: LocalBackupFile, target: UploadTarget) {
        access { newRevision(file, target, UploadState.PENDING) }
    }
    override suspend fun claim(revision: UploadRevision): UploadToken? = access {
        if (transition(revision, UploadState.PENDING, UploadState.UPLOADING) == Transition.APPLIED) UploadToken(revision) else null
    }
    override suspend fun isCurrent(token: UploadToken) = access(publish = false) {
        select("id = ? AND revision = ? AND state = 'UPLOADING'",
            arrayOf(token.revision.id.toString(), token.revision.number.toString())).isNotEmpty()
    }
    override suspend fun hasCollision(entry: UploadEntry) = access(publish = false) {
        select("account = ? AND remote = ? AND source != ?",
            arrayOf(entry.target.account.value, entry.target.path.value, entry.file.uri)).isNotEmpty()
    }
    override suspend fun completeUpload(token: UploadToken) = access {
        transition(token.revision, UploadState.UPLOADING, UploadState.UPLOADED)
    }
    override suspend fun failUpload(token: UploadToken, failure: UploadFailure) = access {
        transition(token.revision, UploadState.UPLOADING,
            if (failure.automaticRetry) UploadState.PENDING else UploadState.ATTENTION, failure)
    }
    override suspend fun deferUpload(token: UploadToken) = access {
        transition(token.revision, UploadState.UPLOADING, UploadState.PENDING)
    }
    override suspend fun retryFailures() {
        access {
            // Failed local writes are never made uploadable by Retry or reconnecting.
            val protected = UploadFailure.entries.filter { it.requiresLocalVerification }.joinToString(",") { "'${it.name}'" }
            execSQL("UPDATE uploads SET state = 'PENDING' WHERE state = 'ATTENTION' AND failure NOT IN ($protected)")
        }
    }
    override suspend fun forget(revision: UploadRevision) {
        access { delete("uploads", "id = ? AND revision = ? AND state IN ('ATTENTION', 'UPLOADED')",
            arrayOf(revision.id.toString(), revision.number.toString())) }
    }
}
