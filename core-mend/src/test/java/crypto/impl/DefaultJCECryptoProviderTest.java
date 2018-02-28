package crypto.impl;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.crypto.impl.DefaultJCECryptoProvider;
import crypto.CryptoProviderTest;

import java.security.NoSuchAlgorithmException;

public class DefaultJCECryptoProviderTest extends CryptoProviderTest {
    public DefaultJCECryptoProviderTest() {
        super(new DefaultJCECryptoProvider(AppProperties.STANDARD_IV, AppProperties.PREFERRED_AES_KEY_SIZE,
                AppProperties.PREFERRED_AES_ALG, AppProperties.PREFERRED_RSA_ALG));
    }
}
