package co.samco.mendroid.upload

import java.security.MessageDigest

/** Safe to expose in UI state. Credentials are held separately. */
data class NextcloudAccount(val server: String, val userId: String) {
    val id: AccountId get() = AccountId(MessageDigest.getInstance("SHA-256")
        .digest("$server\n$userId".toByteArray()).joinToString("") { "%02x".format(it) })
}

class NextcloudSession(val account: NextcloudAccount, val login: String, val appPassword: String) {
    override fun toString() = "NextcloudSession(account=$account)"
}

sealed interface CredentialRead {
    data class Available(val session: NextcloudSession) : CredentialRead
    data object Missing : CredentialRead
    data object Unavailable : CredentialRead
}

interface CredentialStore {
    fun read(): CredentialRead
    fun save(session: NextcloudSession)
    fun clear()
}
