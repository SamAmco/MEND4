# Nextcloud backups: design and review guide

## Scope

Uploads are additive. Use the user-selected local enc folder and local log
directory; back up completed MEND writes to two separately selected Nextcloud folders.
The phone is authoritative for its locally written logs. There is no remote
deletion, external file watching, or automatic local cleanup. Optional additional
Nextcloud folders supply read-only logs and attachments on demand. Local encrypted files remain
manageable through the user's file manager.

## Review order

The replacement jj stack starts at `n` (`nyvoxlro`, the existing date-scroll change).
Each feature
revision introduces final component implementations and their tests, rather than
introducing a large implementation and then refactoring it in later revisions.

1. **Platform imports.** Independent Kotlin name cleanup, without behavior changes.
2. **Queue contracts.** `UploadModels`, `UploadQueue`, and their tests. Start here
   to understand file identity, destinations, revisions, failures, and legal state
   transitions. Includes test runtime support.
3. **Nextcloud interfaces.** Account/session types, protected credentials, settings,
   shared HTTP mechanics, browser login, and WebDAV listing/staging/publication.
   Protocol and settings tests live with these components.
4. **Local writes and snapshots.** Shared file lock, owned output streams, explicit
   existing-file enqueue, and owned encrypted snapshots. Tests include queue-storage
   failures, cancelled writes, and snapshots taken during local changes.
5. **Background uploads.** Eligibility, runner, scheduling, workers, notification,
   DI bindings, and startup. Runner tests cover serialization, supersession, policy
   changes, cleanup and retries without invoking WorkManager.
6. **MEND write integration.** Attachment/log hooks, explicit encryption success,
   original-file deletion guard, and retention of failed audio recordings.
7. **User controls.** Backup commands and existing-file discovery, coherent read-only
   view-model state, browser effects, cancellable folder navigation, small Compose
   components, localized messages, and setup documentation. Application/view-model
   tests cover explicit verification, old-file backups, reconnect and stale responses.

All implementation paths below are under
`mendroid/src/main/java/co/samco/mendroid/`.

## Responsibilities

| Component | Owns |
| --- | --- |
| `upload/UploadQueue.kt` | Transactional persistence and revision/state checks. Publishes read-only entries. Queries return snapshots without recovery or flow updates; `refreshEntries` explicitly refreshes observers. |
| `upload/UploadSettings.kt` | Account-bound folder selections, network preferences and an outstanding rescan warning. Contains no password in observable state. |
| `upload/EncryptedCredentialStore.kt` | Atomic credential-file format and non-exportable Android Keystore key. |
| `upload/NextcloudLoginClient.kt` | Documented Login Flow v2 and OCS user-ID lookup. |
| `upload/NextcloudHttp.kt` | HTTPS setup validation, credential-origin checks, cancellation, response handling and typed HTTP failures. |
| `upload/WebDavClient.kt`, `WebDavListing.kt` | DAV paths, successful direct-child resource parsing, streaming GET/PUT, explicit MOVE publication, conflict verification and bounded remote cleanup. |
| `upload/LocalBackupWriter.kt` | Local output-stream lifetime and successful-write registration. Local success is independent of queue availability. |
| `upload/UploadSnapshots.kt` | Claim a revision and copy its encrypted bytes under the local-write lock. Explicit ready/superseded/unavailable results and snapshot cleanup. |
| `upload/UploadRunner.kt` | Serialized transfer lifecycle and the decision whether a staged revision may be published. No scheduler or UI dependencies. |
| `upload/UploadScheduler.kt`, `UploadWorker.kt` | WorkManager adaptation, network constraints, backoff and foreground notification. |
| `upload/BackupService.kt`, `DeviceBackupFiles.kt` | Application commands and explicit local-file discovery (logs come from `model/LocalLogDirectory.kt`); presentation does not access SQLite or credentials. |
| `viewmodel/NextcloudViewModel.kt` | One read-only UI state, browser effects, independently tracked login/import/browse operations and stale-response prevention. |
| `ui/screens/Nextcloud*.kt` | Setup, destinations, folder picker, upload rows and string-resource presentation. |

## Enforced contracts

- `allEntries`, `pending`, `isCurrent`, and `hasCollision` only query the database.
  `refreshEntries` explicitly reloads the UI flow. Bulk backup filters a returned
  `allEntries` snapshot instead of reading the UI flow after an unrelated query.
- `recoverAfterRestart` explicitly recovers prior-process writes and uploads once
  per queue instance. Starting a write or claiming an upload requires that recovery
  to have completed; subsequent calls cannot reset active work. Startup notifications,
  writers, the runner, and bulk backup call recovery explicitly at their entry points.
  Recovering abandoned claims between worker attempts remains separate, under the
  runner's exclusive transfer lock.

- A queue entry retains its original account and remote path. Future destination
  changes never redirect pending work. Existing-file imports capture one destination
  configuration for the whole operation.
- Queue transitions require the expected state and revision. Old completions,
  failures or history removal cannot alter a newer local revision.
- After obtaining exclusive transfer ownership, each runner invocation recovers
  abandoned upload claims. A temporary database failure cannot leave an upload
  permanently stuck until process restart; active local writes are unaffected.
- Record write intent before opening output. The writer owns stream closure and
  only queues a revision after successful closure.
- A failed or interrupted local write requires a new successful write or an explicit
  user-confirmed checked-file backup. Ordinary Retry, reconnect, and bulk existing-file scans cannot enable it.
- Queue failures do not prevent a local save. The result distinguishes save success
  from backup registration, and a persisted rescan warning prevents an "all uploaded"
  claim until a successful explicit scan.
- Snapshot reads share a lock with MEND writes. Network I/O does not hold that lock.
  The runner itself serializes transfers, independently of WorkManager.
- A staged upload is published only after checking its revision and current account/
  pause/network eligibility. Superseded revisions are discarded; the runner can
  process their successor during the same run.
- Typed failures determine retry policy; UI text comes from string resources.
  Observable state never contains the Nextcloud app password.
- Deleting local files never deletes remote backups. Uploads need no MEND private key.

## Additional read folders

`remote/NextcloudReadFiles.kt` lists immediate children through the shared WebDAV
client and downloads selected files. Selections are bound to the connected account;
file access checks that account and direct parent both before and after network
I/O. These sources do not register uploads or modify local directories. Each folder
is listed independently so a failure does not hide local logs or healthy sources.

`remote/AttachmentFiles.kt` resolves readable local attachments first, then looks
for the exact encrypted filename in the additional folders. `SelectLogViewModel`
combines local and remote log entries, distinguishes their origins and refreshes
after folder configuration changes. Every remote open downloads fresh bytes.

`remote/RemoteReadCache.kt` owns encrypted temporary downloads for one unlock
session. `PrivateKeyManager` synchronously ends that session when locking: readers
are cancelled, cached files removed, private keys cleared, and decrypted log state
reset. Generation checks prevent late downloads or decryptions from publishing
results into a later session, even if an operation ignores cancellation. Downloads
are also cleared at startup; they provide no offline availability guarantee.

## Scheduling

Successful local writes request an upload immediately and await WorkManager's
confirmation that the request is persisted. The worker itself requires unmetered
connectivity by default, or any connected network when mobile data is enabled.
Transient transfer failures use exponential backoff. New writes append to existing
work; explicit retries and policy changes replace it with current constraints.
Pause/disconnect cancel scheduled transfers, and resume/reconnect schedule them.

There is no periodic polling or separate network wake-up worker. Startup requests
an upload to recover pending entries if the process died between recording them
and scheduling WorkManager. That small gap is accepted: the queue and WorkManager
use separate databases. Startup does not scan for unqueued local files.

## Upload failure notification

An upload failure posts one ongoing, quiet notification with a Retry action, using
an ID separate from the foreground-service notification. Further failures update
the same notification. The last failure stays recorded while an upload is retried,
including across process restarts; success, replacement by a new local revision,
or forgetting the failed entry clears it. No failure notification is posted for
healthy queued work.

Retry schedules a preparation worker that requeues eligible failures, then awaits
scheduling an upload using the current network policy, bypassing existing backoff.
Repeated taps replace the preparation request. Preparation retries database or
scheduling errors. After it succeeds, only the upload worker repeats on network
failures, so permanent failures are not continually requeued. Automatic and manual uploads use the same serialized
runner and exponential backoff. Ordinary retry never enables failed/interrupted
local writes. Notification posting respects Android's notification permission.

## Database evolution

The upload database starts at schema version 1. There are no migrations for
unpublished development builds. `SQLiteOpenHelper` stores the schema version in
SQLite's `PRAGMA user_version`; it does not require a version table.

After release, schema changes must increment the helper version and implement
transactional, sequential migrations in `onUpgrade`, with tests starting from each
supported released schema. Preserve queued destinations, revisions, and local-write
failure states. Persisted enum names are part of this schema contract too.
Missing migrations fail without dropping the database; downgrades also fail by
default. Do not silently recreate the queue: losing pending work or the distinction
between successful and interrupted local writes would lose backup guarantees.

## Limits and acceptance checks

One account, two backup destinations, and any number of additional read folders. Transfers restart instead of resuming chunks;
large files need enough temporary device space and a compatible server request-size
limit. Lost connections can leave encrypted `.mend-upload-*` siblings on the server.
External file-manager edits cannot participate in MEND's local-write lock.

The debug APK is `mendroid/build/outputs/apk/debug/mendroid-debug.apk`.
Run `./gradlew :mendroid:assembleDebug :mendroid:lintDebug :mendroid:testDebugUnitTest`.
The complete suite has 111 unit tests, including remote reads and lock-session races.
Tests use real SQLite through Robolectric, fake transfer boundaries for runner
contracts, mocked HTTP for WebDAV/login, and controlled coroutine scheduling for
view-model races. Each rebuilt revision is checked before proceeding to the next.

No Android device/emulator or real Nextcloud account is available in this session.
Before relying on a build, verify browser login/2FA, Keystore storage across process
restart, both folder pickers, attachment/log transfers, offline then reconnect,
unmetered/metered policy, app locking during upload, expired credentials, full server
storage and large transfers. Repeatedly append to a log during a slow upload and
compare the final remote encrypted bytes with the latest local file. Confirm local
deletion leaves the remote backup. For additional read folders, verify direct-child
listings, same-name logs in different folders, local attachment precedence, partial
folder failures, fresh remote downloads, account changes, and locking during a
download. Confirm locking clears cached downloads and no late result restores them.
