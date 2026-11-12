# Android Nextcloud integration

This guide is for developers changing the Android app's Nextcloud integration.
For connecting an account, choosing folders, and managing uploads, see the
[Android user guide](../mendroid/README.md#optional-nextcloud-uploads).

MEND uploads encrypted attachments and logs to a single Nextcloud account. Local
files are the source of truth: log uploads replace the remote log, and deleting a
local file does not delete its remote copy. Additional Nextcloud folders provide
read-only access to remote logs and attachments. There is no two-way sync,
recursive folder search, or monitoring of edits made by other apps.

## Where to start

Implementation paths below are relative to
[`mendroid/src/main/java/co/samco/mendroid`](../mendroid/src/main/java/co/samco/mendroid).

| Component | Responsibility |
| --- | --- |
| [`nextcloud/`](../mendroid/src/main/java/co/samco/mendroid/nextcloud) | Account settings, credentials, TLS, browser login, and shared HTTP/WebDAV operations. |
| [`LocalBackupWriter`](../mendroid/src/main/java/co/samco/mendroid/upload/LocalBackupWriter.kt) | Writes local encrypted files and registers successful saves for upload. |
| [`UploadQueue`](../mendroid/src/main/java/co/samco/mendroid/upload/UploadQueue.kt) | SQLite persistence, revision checks, state transitions, and crash recovery. |
| [`UploadRunner`](../mendroid/src/main/java/co/samco/mendroid/upload/UploadRunner.kt) | Serializes transfers, prepares snapshots, and decides whether to publish them. |
| [`UploadScheduler`](../mendroid/src/main/java/co/samco/mendroid/upload/UploadScheduler.kt) | Schedules WorkManager jobs with network constraints and retry backoff. |
| [`remote/`](../mendroid/src/main/java/co/samco/mendroid/remote) | Remote log discovery, attachment lookup, and downloads tied to an unlock session. |
| [`NextcloudViewModel`](../mendroid/src/main/java/co/samco/mendroid/viewmodel/NextcloudViewModel.kt) | UI commands and state, including login, folder selection, and upload management. |

`BackupService` connects UI commands to upload components. Shared connection types
belong in `nextcloud/`; upload state and retry policy belong in `upload/`.
`NextcloudFailure` describes connection/server errors, while `UploadFailure` also
includes local-write and queue failures.

## From a local save to a remote file

With uploads configured, a save follows this sequence:

1. `LocalBackupWriter` records a write intent and writes the encrypted local file.
   It owns stream closure and queues the revision only after the write succeeds.
2. `UploadScheduler` requests durable WorkManager execution. Saving locally does
   not wait for the network transfer.
3. `UploadSnapshots` claims a queued revision and copies its encrypted bytes into
   a temporary snapshot while holding the same lock used by local writers.
4. `UploadRunner` releases the local-write lock before network I/O. `WebDavClient`
   uploads the snapshot to a temporary `.mend-upload-*` sibling using WebDAV PUT.
5. Before publishing, the runner checks that the revision is still current and
   that the account and upload policy still permit the transfer. WebDAV MOVE
   publishes the final filename. Successful completion removes the queue entry.

Snapshots let local writes continue during a transfer. They are deleted after the
attempt; abandoned snapshots are cleared when snapshot storage is first used in
a new process. Transfers restart from the beginning rather than resuming chunks,
so large files require temporary device space and suitable server size limits.
Failed remote cleanup can leave encrypted temporary siblings on the server.
Other apps do not participate in the local-write lock; external edits can race
with snapshot reads.

Log publication allows replacement. Attachment publication refuses to overwrite
a different remote file; an existing attachment counts as success only if its
encrypted bytes match. Pending entries from different local URIs targeting the
same account and remote path require the user to resolve the collision.

A new local write supersedes an older revision for the same source and destination.
A superseded staged upload is discarded. Changing destination settings affects
future writes; pending entries retain their original account and remote path.

## Queue state and local-write safety

| State | Meaning |
| --- | --- |
| `WRITING` | A local write has started and is not yet safe to upload. |
| `PENDING` | The revision is ready for an upload attempt, possibly following a retryable failure. |
| `UPLOADING` | The runner has claimed this revision. |
| `ATTENTION` | Automatic attempts have stopped; the recorded failure determines the required action. |

There is no completed state: successful publication deletes that revision. An
existing-file scan or another local write can queue the file again. Transitions
check the row ID, revision number, and expected state. IDs use `AUTOINCREMENT`
so delayed callbacks cannot affect new entries after old entries are deleted.

`recoverAfterRestart` runs once per queue instance. It returns abandoned uploads
to `PENDING` and marks interrupted writes as needing local verification. The runner
also recovers abandoned upload claims under its exclusive transfer lock between
attempts. Database reads do not perform recovery or refresh observable state;
`refreshEntries` explicitly refreshes the UI snapshot.

Local log appends record the original file length and truncate back to it if the
append fails in the running process. Process death cannot run that rollback.
Failed or interrupted local writes must therefore remain protected until a new
successful write or explicit verification of that file. Ordinary Retry, reconnect,
and bulk existing-file scans must not make those files uploadable. Snapshot
preparation checks protection across destinations for the same local source.

Queue registration errors do not undo a successful local save. The caller receives
a separate upload-registration result, and a rescan warning tells the user to
check local files and use **Upload existing local files**.

## Scheduling, retries, and notifications

`UploadWorker` runs the serialized upload runner. WorkManager requires an unmetered
network by default, or any connected network when metered connections are enabled.
Waiting for those constraints is not an upload failure. Connection and temporary
server failures retry with exponential backoff; other failures require attention.
Pause and disconnect cancel scheduled work.

`UploadRetryWorker` handles explicit Retry actions. It requeues eligible failures
and schedules an upload with the current policy, replacing the existing backoff.
It does not perform the network transfer itself or bypass local-write protection.

There is no periodic scan or separate wake-up worker. Queue persistence and
WorkManager scheduling use separate databases; app startup schedules pending work
again to recover a process death between those operations. Startup does not scan
local folders for unqueued files.

`UploadFailureNotifications` observes persisted failures and maintains one quiet,
ongoing notification with Retry. It remains during retries and requires Android
notification permission and an enabled channel. This is separate from the
foreground notification used for large transfers. Uploading encrypted files does
not require the MEND private key, so uploads can continue while MEND is locked.

## Login, credentials, and certificate trust

`NextcloudLoginClient` uses Nextcloud Login Flow v2: browser approval yields an app
password, then an OCS user lookup resolves the user ID used in WebDAV paths.
`NextcloudHttp` enforces HTTPS, credential-origin checks, and disabled redirects.
WebDAV uses PROPFIND for listings, GET for downloads, and PUT/MOVE for publication.

The app password is encrypted with an Android Keystore key and stored privately
on the device. It must not enter observable UI state or logs. Uploads contain
encrypted attachments and logs, not plaintext, private keys, or `config.xml`.
Android system backups and device-to-device app-data transfers are disabled for
the entire app in every build variant.

Platform TLS trust is the default. Connect can explicitly approve a self-signed
certificate and persist its exact public certificate for that HTTPS hostname and
port. Hostname and expiry checks still apply. Background requests cannot approve
a new certificate; a changed self-signed certificate requires Connect approval
again. Browser login has its own certificate trust policy.

## Remote reads and locking

`NextcloudReadFiles` lists direct children of the configured additional folders.
Opening a remote log downloads fresh encrypted bytes. `AttachmentFiles` checks
for an exact filename in the local enc directory first, then the additional
folders. Remote sources do not register uploads or modify local files.

`RemoteReadCache` owns encrypted downloads for one unlock session. Locking MEND
cancels readers and clears those downloads. Session-generation checks reject late
results even if an operation ignores cancellation. Downloads provide no offline
availability guarantee. Keep this lifetime separate from upload snapshots, which
must remain usable while MEND is locked.

## Maintaining and testing the integration

`UploadQueue` owns the database schema version and migrations through
`SQLiteOpenHelper`. Migrations must preserve unfinished destinations, revisions,
and local-write protection. Persisted enum names are part of the schema contract.
Never recover a schema error by wiping the queue. The existing migration also
covers removal of completed entries and preservation of monotonically increasing IDs.

Tests live under
[`mendroid/src/test/java/co/samco/mendroid`](../mendroid/src/test/java/co/samco/mendroid).
Queue and migration tests use SQLite through Robolectric; runner tests control
transfer boundaries; HTTP tests cover login, WebDAV, and TLS; remote-read tests
exercise account changes and lock-session cancellation. Extend the relevant tests
when changing those contracts. Build prerequisites and commands are in the
[Android development guide](../mendroid/README.md#development-checks).

Device checks are still needed for browser approval, Android permissions,
WorkManager network restrictions, notifications, and gesture-driven UI. When
changing transfer behavior, also check repeated log writes during an upload and
locking during a remote download.
