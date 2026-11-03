package co.samco.mendroid.upload

import co.samco.mendroid.nextcloud.NextcloudSession
import co.samco.mendroid.nextcloud.RemotePath
import java.io.File

/** A completed temporary upload. The runner owns the decision to publish it. */
interface StagedUpload {
    suspend fun publish(policy: OverwritePolicy)
    suspend fun discard()
}

interface UploadTransport {
    suspend fun stage(session: NextcloudSession, path: RemotePath, file: File, progress: (Long) -> Unit): StagedUpload
}

