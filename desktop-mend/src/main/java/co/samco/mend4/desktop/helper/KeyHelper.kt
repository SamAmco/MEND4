package co.samco.mend4.desktop.helper

import co.samco.mend4.core.crypto.CryptoProvider
import java.nio.file.Files
import java.security.PrivateKey
import javax.inject.Inject

class KeyHelper @Inject constructor(
    private val fileResolveHelper: FileResolveHelper,
    private val cryptoProvider: CryptoProvider,
) {
    val privateKey: PrivateKey?
        get() {
            val privateKeyFile = fileResolveHelper.privateKeyFile
            if (!fileResolveHelper.isFile(privateKeyFile)) return null
            val privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath())
            return cryptoProvider.getPrivateKeyFromBytes(privateKeyBytes)
        }
}