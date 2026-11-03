package co.samco.mendroid.upload

import org.junit.Assert.*
import org.junit.Test

class UploadModelsTest {
    @Test fun pathsHaveOneCanonicalRepresentation() {
        assertEquals(RemotePath.of("/a/b"), RemotePath.of("//a//b/"))
        assertEquals(RemotePath.ROOT, RemotePath.of("/phone.mend").parent)
        assertEquals(RemotePath.of("/a/file.enc"), RemotePath.of("/a").child("file.enc"))
    }
    @Test fun pathsCannotEscapeTheirSelectedFolder() {
        for (path in listOf("relative", "/a/../b", "/./b")) {
            try { RemotePath.of(path); fail(path) } catch (_: IllegalArgumentException) { }
        }
        for (name in listOf("", ".", "..", "a/b")) {
            try { RemotePath.ROOT.child(name); fail(name) } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun localWriteFailuresAreNeverAutomaticallyRetryable() {
        for (failure in UploadFailure.entries.filter { it.requiresLocalVerification }) assertFalse(failure.automaticRetry)
    }
    @Test fun overwritePolicyIsExplicitForEachFileKind() {
        assertEquals(OverwritePolicy.REPLACE, UploadKind.LOG.overwrite)
        assertEquals(OverwritePolicy.REQUIRE_IDENTICAL, UploadKind.ATTACHMENT.overwrite)
    }
}
