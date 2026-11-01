package co.samco.mend4.desktop.helper.impl

import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.helper.KeyHelper
import java.nio.file.Files
import java.security.PrivateKey
import javax.inject.Inject

class KeyHelperImpl @Inject constructor(
    private val fileResolveHelper: FileResolveHelper,
    private val cryptoProvider: CryptoProvider,
) : KeyHelper {
    override val privateKey: PrivateKey?
        get() {
            // Never revive the old disk-backed unlock, even if a stale prKey exists.
            return null
        }
}
