package co.samco.mendroid.upload

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface NetworkStatus { fun isMetered(): Boolean }
class AndroidNetworkStatus @Inject constructor(@ApplicationContext private val context: Context) : NetworkStatus {
    override fun isMetered() = context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
}

sealed interface UploadPermission {
    data class Allowed(val session: NextcloudSession) : UploadPermission
    data class Rejected(val failure: UploadFailure) : UploadPermission
    data object Waiting : UploadPermission
}

class UploadEligibility @Inject constructor(
    private val settings: UploadSettings,
    private val credentials: CredentialStore,
    private val network: NetworkStatus
) {
    fun check(account: AccountId): UploadPermission {
        val config = settings.config.value
        if (config.paused || !config.mobileData && network.isMetered()) return UploadPermission.Waiting
        val session = (credentials.read() as? CredentialRead.Available)?.session
        return if (config.account?.id == account && session?.account?.id == account) UploadPermission.Allowed(session)
        else UploadPermission.Rejected(UploadFailure.ACCOUNT_UNAVAILABLE)
    }
}
