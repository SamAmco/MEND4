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
    for shell in ("/bin/bash", "/bin/zsh"):
        t = Terminal(home, shell)
        try:
            t.expect("Password:")
            t.send("test-password\n")
            t.expect("[mend unlocked]")
            t.send("printf 'BROKER=%s\\n' \"$PPID\"\n")
            t.expect("BROKER=")
            # Skip the echoed command's format string; consume the actual numeric PID.
            t.expect("BROKER=")
            broker_line = t.expect("\r\n").strip()
            broker_pid = int(broker_line)
            if Path("/proc").exists():
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
            t.expect("[mend unlocked]")
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
            t.send("sleep 30\n")
            time.sleep(.2)
            t.send("\x1a")
            t.expect("[mend unlocked]")
            t.send("kill %1; lock\n")
            t.expect("MEND locked.")
            assert not list(Path(home).rglob("prKey"))
            assert Path(home, "output.txt").read_text() == "BINARY_TEST_CONTENT"
            print(f"PASS: {shell}: bootstrap, decrypt, pipe, redirect, nested shell, mend guard, job control, lock")
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
            print(f"PASS: {shell}: timeout warning, Enter renewal, expiry")
        finally:
            t.close()
