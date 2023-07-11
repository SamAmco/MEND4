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
            val privateKeyFile = fileResolveHelper.privateKeyFile
            if (!fileResolveHelper.isFile(privateKeyFile)) return null
            val privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath())
            return cryptoProvider.getPrivateKeyFromBytes(privateKeyBytes)
        }
}