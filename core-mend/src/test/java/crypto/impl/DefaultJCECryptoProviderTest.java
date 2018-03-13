package crypto.impl;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.IBase64EncodingProvider;
import co.samco.mend4.core.crypto.impl.DefaultJCECryptoProvider;
import crypto.CryptoProviderTest;

import java.util.Base64;

public class DefaultJCECryptoProviderTest extends CryptoProviderTest {
    public DefaultJCECryptoProviderTest() {
        super(new DefaultJCECryptoProvider(AppProperties.STANDARD_IV, AppProperties.PREFERRED_AES_KEY_SIZE,
                AppProperties.PREFERRED_RSA_KEY_SIZE, AppProperties.PREFERRED_AES_ALG, AppProperties.PREFERRED_RSA_ALG,
                AppProperties.PASSCHECK_SALT, AppProperties.AES_KEY_GEN_ITERATIONS, new IBase64EncodingProvider() {
            @Override
            public byte[] decodeBase64(String base64String) {
                return Base64.getDecoder().decode(base64String);
            }

            @Override
            public String encodeBase64URLSafeString(byte[] data) {
                return Base64.getEncoder().encodeToString(data);
            }
        }));
    }
}

