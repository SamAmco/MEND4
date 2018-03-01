package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.exception.CorruptSettingsException;
import org.apache.commons.codec.binary.Base64;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class KeyHelper {
    private final Settings settings;
    private final FileResolveHelper fileResolveHelper;
    private final OSDao osDao;

    @Inject
    public KeyHelper(Settings settings, FileResolveHelper fileResolveHelper, OSDao osDao) {
        this.settings = settings;
        this.fileResolveHelper = fileResolveHelper;
        this.osDao = osDao;
    }

    public RSAPrivateKey getPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        byte[] keyBytes = osDao.readAllFileBytes(Paths.get(fileResolveHelper.getPrivateKeyPath()));
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(keyBytes);
        return (RSAPrivateKey) kf.generatePrivate(privateKeySpec);
    }

    public RSAPublicKey getPublicKey() throws IOException, CorruptSettingsException,
            NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        String pubKeyString = settings.getValue(Settings.Name.PUBLICKEY);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.decodeBase64(pubKeyString));
        return  (RSAPublicKey) kf.generatePublic(publicKeySpec);
    }
}
