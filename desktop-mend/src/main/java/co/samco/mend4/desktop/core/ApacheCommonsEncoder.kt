package co.samco.mend4.desktop.core

import co.samco.mend4.core.IBase64EncodingProvider
import org.apache.commons.codec.binary.Base64

class ApacheCommonsEncoder : IBase64EncodingProvider {
    override fun decodeBase64(base64String: String): ByteArray {
        return Base64.decodeBase64(base64String)
    }

    override fun encodeBase64URLSafeString(data: ByteArray): String {
        return Base64.encodeBase64URLSafeString(data)
    }
}