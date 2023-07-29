package crypto

import co.samco.mend4.core.IBase64EncodingProvider
import java.util.Base64

class TestHelpers {
    companion object {
        fun getEncoderImplementation() = object : IBase64EncodingProvider {
            override fun decodeBase64(base64String: String): ByteArray {
                return Base64.getUrlDecoder().decode(base64String)
            }

            override fun encodeBase64URLSafeString(data: ByteArray): String {
                return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
            }

        }
    }
}