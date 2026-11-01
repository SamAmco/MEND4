# Unlock sessions: review guide

Desktop requires Java 21+, Linux or macOS, and Bash or Zsh. The Android module retains its existing Java target and in-memory key manager. Linux terminal tests exercise both shells; macOS still needs on-device verification.

## Revision order

1. **Prepare Java 21 and stream-oriented session decryption** — `SessionDecrypt` consumes an in-memory key and writes either stdout or an owner-only file. Temporary decoded private-key bytes are cleared in the shared crypto provider.
2. **Add authenticated Unix-domain session protocol with bounded framing** — `SessionProtocol` and `SessionBroker`. Independent connections carry a version, credential, working directory and argument vector. Responses distinguish binary stdout, stderr and exit status. Tests cover binary framing and rejected authentication. No TCP fallback.
3. **Replace disk-backed unlock with piped Bash and Zsh sessions** — `UnlockSession`, `SessionShell`, and `resources/session/`. Password changes authenticate directly instead of reading `prKey`.
4. **Add configurable idle timeout and shell prompt renewal** — `SessionTimeout`, prompt hooks and real-terminal smoke tests.
5. **Protect Linux key processes from memory dumps and JVM attachment** — `MemoryProtection` isolates the OS-specific code. Live tests check denied memory reads and diagnostic attachment.
6. **Finish migration and document session security and review steps** — standalone merge authentication, regression checks and this guide.

Use `jj log` and `jj diff -r <change-id>` to review each layer. Intermediate revisions add infrastructure; the shell replacement is where `mend unlock` changes behaviour.

## Runtime flow

`mend unlock` starts a JVM with diagnostic attachment, automatic heap dumps, and crash core dumps disabled before prompting. On Linux it also calls `prctl(PR_SET_DUMPABLE, 0)` via JNA, verifies the flag, and refuses to proceed if that fails. A small outer JVM waits for this protected process and forwards shutdown by terminating it. No passwords or keys exist in that outer JVM.

The protected `UnlockSession` process supervises two children: the interactive shell and a separate, equally protected `SessionKeyProcess` JVM. It reads the password and sends its characters over an anonymous pipe to the key process; both processes clear their password arrays. Only the key process decrypts and retains the `PrivateKey`. It starts the broker and returns the socket path and credential over another anonymous pipe. The supervisor keeps the key process's stdin open while the shell lives; EOF requests locking if the shell exits or the supervisor dies.

The broker's Unix-domain socket lives in a fresh mode-0700 `/tmp/mend-*` directory. The directory contains static, non-secret shell startup scripts and a socket entry. The socket entry stores no message data; messages exist in process/kernel memory. No credentials or private keys are written to those files.

The shell starts interactively with stdout/stderr inherited and stdin connected to a normal `ProcessBuilder` anonymous pipe. Its startup script reads four lines: credential, socket path, Java executable, classpath. It closes/replaces that stdin with `/dev/tty`, then enables job control. This avoids custom descriptor-inheritance code. Shell configuration is loaded before reading secrets; it is part of the trusted environment.

The startup script defines `dec`, `merge`, `lock`, and a refusing `mend` function. `MEND_SESSION_ACTIVE` also makes the global application refuse calls from that environment. The credential is a non-exported variable; a builtin `printf` delivers it to each client over stdin. Paths/arguments are quoted, and arguments are never evaluated as shell code. Tracing and automatic variable export are disabled before reading or passing secrets.

Normal pipelines and forked shell subshells inherit shell state, so `(dec journal)` and `dec journal | less` work. A newly executed Bash/Zsh does not inherit the functions or credential. This is an inheritance convention, **not** a security boundary against malicious shell code: `source`, shell functions, debugging hooks, explicitly exporting secrets, or commands executed in the current shell can access its variables. The `mend` refusal can also be deliberately bypassed by clearing its marker.

Each client connects with a fresh socket. The broker authenticates a random 256-bit credential before executing an allowlisted operation (`dec`, `merge`, `lock`, `touch`). It limits simultaneous clients to 16 and closes incomplete handshakes after five seconds. Arguments and frames have bounded sizes. Requests include the client's working directory, so `cd` and relative paths work as expected. There is no Java object deserialization, arbitrary command execution API, key export, or network listener.

Locking closes the listener and all active sockets, then exits the key-owning JVM. The `lock` shell function also exits the session shell. Normal shell exit closes the broker's control pipe and waits for its shutdown, forcing termination if needed. The supervisor removes the known startup files and empty directory when the shell ends. Terminal hangup also triggers cleanup. SIGKILL cannot run cleanup: it may leave a harmless stale socket directory and an orphan shell. A killed supervisor closes the control pipe, asking the key process to exit. Background jobs and external viewers are not a managed sandbox.

## Timeouts and plaintext

The default inactivity timeout is 15 minutes. Set `mend set session-timeout 30m`, override with `mend unlock --timeout 2h`, or disable with `--no-timeout` / setting `none`. Durations accept positive integer `s`, `m` or `h`, up to one year.

Authenticated requests and arrival at a shell prompt renew the monotonic clock. A warning appears up to 30 seconds before expiry (one third of short timeouts). Pressing Enter **at the prompt** renews it. Typing into a viewer or running a long foreground job is not observable shell-prompt activity. The timer itself never consumes terminal input; its warning may draw over a foreground application's screen.

Expiry closes the broker and exits the key process, interrupting any ongoing decryption. The supervisor continues waiting for the shell, preserving terminal ownership for foreground applications. For example, `dec journal | vim -` leaves Vim running with the plaintext it already received. Further decryption requests fail. On returning to the shell, its prompt hook detects the unavailable broker and exits. At an idle prompt, Enter triggers that same check. Static startup files remain until the shell exits; the socket is removed as soon as the broker closes.

Logs print to stdout. `.enc` files normally decrypt to a unique mode-0600 file in `dec-dir` and open it using the existing desktop opener; `-s` suppresses opening. `--stdout` streams a file for pipelines or redirection. Redirected output is subject to the user's shell umask. Viewers, terminal scrollback, redirected output, clipboard managers and their caches may retain plaintext. Locking cannot recall output already delivered.

`SessionOptions` combines timeout and auto-clean options. By default, the supervisor invokes the existing `Clean` command implementation after the shell ends and the key process has stopped. Set `session-auto-clean` to `false` to disable this globally; `--auto-clean` and `--no-auto-clean` override the setting for one unlock. The flags can be combined with timeout options in either order; conflicting or repeated options are rejected. Cleanup acts on the entire configured `dec-dir`, including files created by other sessions, using `shred-command`. It does not follow the provenance of individual files or remove redirected output elsewhere. It waits for foreground jobs to finish and the session shell to exit rather than running immediately at key expiry. Failed unlocks do not clean files. Cleaning waits for its subprocesses and reports failures; it cannot run after SIGKILL. With auto-clean disabled, use `mend clean` outside the session when needed.

## Security limits

The private key is never intentionally persisted. Clearing temporary arrays and terminating the process reduce exposure but do not guarantee JVM/provider copies were zeroed or never swapped. Memory locking, swap/hibernation exclusion, hardware-backed keys and verified execution are outside this implementation.

Linux protection blocks ordinary unprivileged process inspection; it does not defeat root, a compromised kernel, injected agents, replaced binaries, malicious startup scripts or a compromised user account. Unix socket permissions and the session token also do not sandbox same-UID malware: it can tamper with user-owned files, shell memory where permitted, or the socket namespace. The shell's credential allows decryption while the session is alive even though it is not the private key. macOS receives the JVM protections but not the Linux `prctl` protection.

References: [Linux PR_SET_DUMPABLE](https://man7.org/linux/man-pages/man2/PR_SET_DUMPABLE.2const.html), [JVM diagnostic options](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html), [JNA](https://github.com/java-native-access/jna).

## Migration and validation

Existing `config.xml` and encrypted data need no conversion. There is no compatibility layer for plaintext key files: sessions neither read nor check for them.

`dec`, `merge` and `lock` exist only inside an unlocked session. `SessionMerge` uses the broker's existing key to sort and deduplicate log entries while preserving their encrypted records. It supports a separate output or explicit `-1`/`-2` replacement. The old global merge command and one-shot key helper have been removed. `mend change-password` runs outside the session and authenticates for its single operation. Normal encryption remains available outside the session without unlocking.

Merge output is staged beside its destination and atomically replaces it only after both logs have been processed successfully. Invalid input leaves existing files intact; ordinary failures remove the staging file. Forced process termination can leave an encrypted staging file. Filesystems that cannot perform the atomic replacement cause the operation to fail. Setup and password changes clear rejected candidates, confirmation arrays and accepted password arrays on success or failure.

Run:

```sh
./gradlew :core-mend:test :desktop-mend:test :desktop-mend:jar
python3 desktop-mend/test/session_pty.py
```

The terminal harness uses only Python's standard library and disposable test keys/configuration. It covers both shells, stdout/pipes/redirection, relative paths, file output, nested shells, global-command refusal, job control, lock, timeout warning/renewal/expiry, key-process exit while Vim remains usable, and Linux memory/JVM attachment protection. It never opens the real user's MEND configuration. Running it requires Bash, Zsh, Vim, Java/JDK tools and Gradle cache access. Desktop-open UI integration on macOS requires a manual smoke test.
