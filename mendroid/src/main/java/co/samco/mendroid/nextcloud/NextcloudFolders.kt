package co.samco.mendroid.nextcloud

interface RemoteFolderBrowser {
    suspend fun folders(session: NextcloudSession, path: RemotePath): List<RemoteFolder>
}


data class RemoteFolder(val name: String, val path: RemotePath)
