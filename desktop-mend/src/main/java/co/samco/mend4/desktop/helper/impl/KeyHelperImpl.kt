package co.samco.mend4.desktop.helper.impl

import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.helper.KeyHelper
import java.security.PrivateKey
import javax.inject.Inject

class KeyHelperImpl @Inject constructor(
    private val cryptoProvider: CryptoProvider,
    private val os: OSDao,
) : KeyHelper {
    // Used by the standalone merge command. Its process exits after the one operation.
    override val privateKey: PrivateKey? by lazy {
        val password = os.readPassword("Password: ")
        val result = try { cryptoProvider.unlock(password) } finally { password.fill('\u0000') }
        (result as? UnlockResult.Success)?.privateKey
    }
}
