package co.samco.mendroid.upload

import org.junit.Assert.*
import org.junit.Test

class UploadModelsTest {
    @Test fun localWriteFailuresAreNeverAutomaticallyRetryable() {
        for (failure in UploadFailure.entries.filter { it.requiresLocalVerification }) assertFalse(failure.automaticRetry)
    }
    @Test fun overwritePolicyIsExplicitForEachFileKind() {
        assertEquals(OverwritePolicy.REPLACE, UploadKind.LOG.overwrite)
        assertEquals(OverwritePolicy.REQUIRE_IDENTICAL, UploadKind.ATTACHMENT.overwrite)
    }
}
