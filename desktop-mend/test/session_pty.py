"""Real terminal smoke tests for Bash/Zsh. Run from the repo root after building the jar.

Uses only Python's standard library. Every key/config/output belongs to a disposable directory.
"""
import os
import pty
import select
import signal
import subprocess
import tempfile
import time
from pathlib import Path

JAR = str(Path("desktop-mend/build/libs/mend.jar").resolve())


class Terminal:
    def __init__(self, home, shell, extra=()):
        self.pid, self.fd = pty.fork()
        if self.pid == 0:
            os.environ["SHELL"] = shell
            os.environ["TERM"] = "dumb"
            os.chdir(home)
            # The JVM fixture directory also isolates shell startup files.
            os.execvp("env", ["env", "HOME=" + home, "java", "-Duser.home=" + home,
                              "-jar", JAR, "unlock", *extra])
        self.pending = b""

    def expect(self, text, timeout=15):
        deadline = time.monotonic() + timeout
        needle = text.encode()
        while needle not in self.pending:
            if time.monotonic() > deadline:
                raise AssertionError(f"Missing {text!r}; output={self.pending!r}")
            if select.select([self.fd], [], [], .1)[0]:
                try:
                    data = os.read(self.fd, 65536)
                except OSError:
                    data = b""
                if not data:
                    raise AssertionError(f"Terminal exited before {text!r}: {self.pending!r}")
                self.pending += data
        result, self.pending = self.pending.split(needle, 1)
        return result

    def send(self, text):
        os.write(self.fd, text.encode())

    def key_process_pid(self):
        self.send("printf 'SUPERVISOR=%s\\n' \"$PPID\"\n")
        self.expect("SUPERVISOR=")  # echoed command
        self.expect("SUPERVISOR=")
        supervisor = int(self.expect("\r\n").strip())
        self.expect("[mend unlocked]")
        children = subprocess.check_output(
            ["ps", "-o", "pid=,args=", "--ppid", str(supervisor)], text=True)
        return next(int(line.split()[0]) for line in children.splitlines()
                    if "--session-broker" in line)

    def close(self):
        try:
            os.kill(self.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        os.waitpid(self.pid, 0)
        os.close(self.fd)


with tempfile.TemporaryDirectory(prefix="mend-pty-") as home:
    subprocess.run(["./gradlew", ":desktop-mend:sessionFixture", "-PfixtureHome=" + home,
                    "--console=plain"], check=True)
    for obsolete in ("dec", "lock", "merge"):
        result = subprocess.run(["java", "-Duser.home=" + home, "-jar", JAR, obsolete], capture_output=True)
        assert result.returncode != 0, f"Obsolete global {obsolete} is still registered"
    for shell in ("/bin/bash", "/bin/zsh"):
        t = Terminal(home, shell)
        try:
            t.expect("Password:")
            t.send("test-password\n")
            t.expect("[mend unlocked]")
            if Path("/proc").exists():
                broker_pid = t.key_process_pid()
                try:
                    fd = os.open(f"/proc/{broker_pid}/mem", os.O_RDONLY)
                except PermissionError:
                    pass
                else:
                    os.close(fd)
                    raise AssertionError("Broker memory was readable")
                attached = subprocess.run(["jcmd", str(broker_pid), "VM.version"],
                                          capture_output=True, timeout=15)
                assert attached.returncode != 0, "JVM diagnostic attachment should be disabled"
            t.send("dec journal | cat\n")
            t.expect("SESSION_TEST_PLAINTEXT")
            t.expect("[mend unlocked]")
            t.send("dec --stdout document.enc > output.txt; cat output.txt\n")
            t.expect("BINARY_TEST_CONTENT")
            t.expect("[mend unlocked]")
            t.send("mkdir -p nested; cd nested; dec --stdout ../document.enc; cd ..\n")
            t.expect("BINARY_TEST_CONTENT")
            t.expect("[mend unlocked]")
            t.send("env | grep '^_mend_' | wc -l\n")
            t.expect("\r\n0\r\n")
            t.expect("[mend unlocked]")
            t.send("mend unlock\n")
            t.expect("mend is unavailable")
            t.expect("[mend unlocked]")
            t.send("bash -c 'type dec'\n")
            t.expect("not found")
            t.expect("[mend unlocked]")
            t.send("dec -s document.enc\n")
            t.expect("Decrypted to:")
            t.expect("[mend unlocked]")
            for command in (
                "merge first.mend second.mend merged; dec merged.mend > merged.txt",
                "cp first.mend in-first.mend; merge -1 in-first.mend second.mend; dec in-first.mend > in-first.txt",
                "cp second.mend in-second.mend; merge -2 first.mend in-second.mend; dec in-second.mend > in-second.txt",
                "cd nested; merge ../first.mend ../second.mend relative; dec relative.mend > relative.txt; cd ..",
            ):
                t.send(command + "\n")
                t.expect("Merged to file:")
                t.expect("[mend unlocked]")
            for output in ("merged.txt", "in-first.txt", "in-second.txt", "nested/relative.txt"):
                text = Path(home, output).read_text()
                assert text.index("MERGE_ONE") < text.index("MERGE_TWO") < text.index("MERGE_THREE")
                assert text.count("MERGE_THREE") == 1
            t.send("sleep 30\n")
            time.sleep(.2)
            t.send("\x1a")
            t.expect("[mend unlocked]")
            t.send("kill %1; lock\n")
            t.expect("MEND locked.")
            assert not list(Path(home).rglob("prKey"))
            assert Path(home, "output.txt").read_text() == "BINARY_TEST_CONTENT"
            print(f"PASS: {shell}: bootstrap, decrypt, session merge (new/-1/-2/cwd), pipes, job control, lock")
        finally:
            t.close()
    for shell in ("/bin/bash", "/bin/zsh"):
        t = Terminal(home, shell, ("--no-timeout",))
        try:
            t.expect("Password:")
            t.send("test-password\n")
            t.expect("[mend unlocked]")
            t.send("exit\n")
            t.expect("MEND locked.")
            print(f"PASS: {shell}: explicit exit with timeout disabled")
        finally:
            t.close()
    for shell in ("/bin/bash", "/bin/zsh"):
        t = Terminal(home, shell, ("--timeout", "6s"))
        try:
            t.expect("Password:")
            t.send("test-password\n")
            t.expect("[mend unlocked]")
            t.expect("MEND will lock shortly")
            t.send("\n")
            t.expect("[mend unlocked]")
            t.expect("MEND will lock shortly")
            t.expect("MEND session timed out.")
            t.expect("MEND locked.")
            # Expiry leaves the shell alone until its next prompt hook notices the lock.
            t.send("\n")
            t.expect("MEND session unavailable:")
            print(f"PASS: {shell}: timeout warning, Enter renewal, expiry")
        finally:
            t.close()
    for shell in ("/bin/bash", "/bin/zsh"):
        t = Terminal(home, shell, ("--timeout", "6s"))
        try:
            t.expect("Password:")
            t.send("test-password\n")
            t.expect("[mend unlocked]")
            broker_pid = t.key_process_pid() if Path("/proc").exists() else None
            saved = Path(home, "vim-after-timeout.txt")
            saved.unlink(missing_ok=True)
            t.send("dec journal | vim -Nu NONE -n -i NONE -\n")
            t.expect("SESSION_TEST_PLAINTEXT")
            t.expect("MEND session timed out.")
            t.expect("MEND locked.")
            if broker_pid is not None:
                deadline = time.monotonic() + 5
                while Path(f"/proc/{broker_pid}").exists() and time.monotonic() < deadline:
                    time.sleep(.05)
                assert not Path(f"/proc/{broker_pid}").exists(), "Key JVM survived expiry"
            # Vim must still accept commands and have its original plaintext buffer.
            t.send(":call writefile(getline(1, '$'), 'vim-after-timeout.txt')\n")
            deadline = time.monotonic() + 5
            while not saved.exists() and time.monotonic() < deadline:
                time.sleep(.05)
            assert "SESSION_TEST_PLAINTEXT" in saved.read_text()
            t.send(":q!\n")
            t.expect("MEND session unavailable:")
            print(f"PASS: {shell}: key JVM exits on timeout; foreground Vim remains usable")
        finally:
            t.close()
