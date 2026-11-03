package co.samco.mendroid.upload

internal class MemoryCredentials : CredentialStore {
    var value: CredentialRead = CredentialRead.Missing
    override fun read() = value
    override fun save(session: NextcloudSession) { value = CredentialRead.Available(session) }
    override fun clear() { value = CredentialRead.Missing }
}

