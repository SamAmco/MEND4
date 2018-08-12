package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.crypto.CryptoProvider;
import co.samco.mend4.core.exception.CorruptSettingsException;
import org.apache.commons.codec.binary.Base64;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;

//TODO print some sort of helpful message if mend is not unlocked
public class KeyHelper {
    private final Settings settings;
    private final FileResolveHelper fileResolveHelper;
    private final CryptoProvider cryptoProvider;
    private final OSDao osDao;

    @Inject
    public KeyHelper(Settings settings, FileResolveHelper fileResolveHelper, CryptoProvider cryptoProvider, OSDao osDao) {
        this.settings = settings;
        this.fileResolveHelper = fileResolveHelper;
        this.cryptoProvider = cryptoProvider;
        this.osDao = osDao;
    }

    public RSAPrivateKey getPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        Path privateKeyPath = Paths.get(fileResolveHelper.getPrivateKeyPath());
        //TODO we should probably wrap the IOException here to provide a more useful message if mend is locked
        byte[] privateKeyBytes = osDao.readAllFileBytes(privateKeyPath);
        return cryptoProvider.getPrivateKeyFromBytes(privateKeyBytes);
    }

    public RSAPublicKey getPublicKey() throws IOException, CorruptSettingsException,
            NoSuchAlgorithmException, InvalidKeySpecException {
        String pubKeyString = settings.getValue(Settings.Name.PUBLICKEY);
        byte[] publicKeyBytes = Base64.decodeBase64(pubKeyString);
        return cryptoProvider.getPublicKeyFromBytes(publicKeyBytes);
    }

    public KeyPair readKeyPairFromFiles(File privateKeyFile, File publicKeyFile)
            throws InvalidKeySpecException, NoSuchAlgorithmException, IOException {
        return cryptoProvider.getKeyPairFromBytes(
                osDao.readAllFileBytes(privateKeyFile.toPath()),
                osDao.readAllFileBytes(publicKeyFile.toPath()));
    }
}
