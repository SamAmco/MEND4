# Nextcloud backups: design and review guide

## Scope

Uploads are additive. Use the user-selected local enc folder and local log
directory; back up completed MEND writes to two separately selected Nextcloud folders.
The phone is authoritative for its logs. No remote deletion, remote download,
external file watching, or automatic local cleanup. Local encrypted files remain
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
| `upload/UploadQueue.kt` | Transactional persistence and revision/state checks. Publishes read-only entries. |
| `upload/UploadSettings.kt` | Account-bound folder selections, network preferences and an outstanding rescan warning. Contains no password in observable state. |
| `upload/EncryptedCredentialStore.kt` | Atomic credential-file format and non-exportable Android Keystore key. |
| `upload/NextcloudLoginClient.kt` | Documented Login Flow v2 and OCS user-ID lookup. |
| `upload/NextcloudHttp.kt` | HTTPS setup validation, credential-origin checks, cancellation, response handling and typed HTTP failures. |
| `upload/WebDavClient.kt`, `WebDavFolders.kt` | DAV paths, successful folder-property parsing, streaming PUT, explicit MOVE publication, conflict verification and bounded remote cleanup. |
| `upload/LocalBackupWriter.kt` | Local output-stream lifetime and successful-write registration. Local success is independent of queue availability. |
| `upload/UploadSnapshots.kt` | Claim a revision and copy its encrypted bytes under the local-write lock. Explicit ready/superseded/unavailable results and snapshot cleanup. |
| `upload/UploadRunner.kt` | Serialized transfer lifecycle and the decision whether a staged revision may be published. No scheduler or UI dependencies. |
| `upload/UploadScheduler.kt`, `UploadWorker.kt` | WorkManager adaptation, network constraints, backoff and foreground notification. |
| `upload/BackupService.kt`, `DeviceBackupFiles.kt` | Application commands and explicit local-file discovery (logs come from `model/LocalLogDirectory.kt`); presentation does not access SQLite or credentials. |
| `viewmodel/NextcloudViewModel.kt` | One read-only UI state, browser effects, independently tracked login/import/browse operations and stale-response prevention. |
| `ui/screens/Nextcloud*.kt` | Setup, destinations, folder picker, upload rows and string-resource presentation. |

## Enforced contracts

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

One account and two remote folders. Transfers restart instead of resuming chunks;
large files need enough temporary device space and a compatible server request-size
limit. Lost connections can leave encrypted `.mend-upload-*` siblings on the server.
External file-manager edits cannot participate in MEND's local-write lock.

The debug APK is `mendroid/build/outputs/apk/debug/mendroid-debug.apk`.
Run `./gradlew :mendroid:assembleDebug :mendroid:lintDebug :mendroid:testDebugUnitTest`.
The complete feature has 79 passing unit tests (60 upload tests, 9 log-directory tests, and 10 existing tests).
Tests use real SQLite through Robolectric, fake transfer boundaries for runner
contracts, mocked HTTP for WebDAV/login, and controlled coroutine scheduling for
view-model races. Each rebuilt revision is checked before proceeding to the next.

No Android device/emulator or real Nextcloud account is available in this session.
Before relying on a build, verify browser login/2FA, Keystore storage across process
restart, both folder pickers, attachment/log transfers, offline then reconnect,
unmetered/metered policy, app locking during upload, expired credentials, full server
storage and large transfers. Repeatedly append to a log during a slow upload and
compare the final remote encrypted bytes with the latest local file. Confirm local
deletion leaves the remote backup and that restoration remains a manual operation.
