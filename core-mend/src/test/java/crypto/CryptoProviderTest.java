package crypto;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.bean.LogDataBlocksAndText;
import co.samco.mend4.core.crypto.CryptoProvider;
import co.samco.mend4.core.exception.MalformedLogFileException;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;

import static org.hamcrest.CoreMatchers.equalTo;

public abstract class CryptoProviderTest {
    private final CryptoProvider cryptoProvider;
    private String newLine = System.getProperty("line.separator");

    private static KeyPair rsaKeyPair;

    public CryptoProviderTest(CryptoProvider cryptoProvider) {
        this.cryptoProvider = cryptoProvider;
    }

    @BeforeClass
    public static void setup() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen;
        keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(AppProperties.PREFERRED_RSA_KEY_SIZE);
        rsaKeyPair = keyGen.genKeyPair();
    }

    @Test
    public void encTest() throws IOException, NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeySpecException,
            MalformedLogFileException {
        String plainText = "The quick brown fox jumped over the lazy dog";
        String fileExtension = "extension";
        byte[] cipherBytes;
        String cipherText;
        try(InputStream inputStream = new ByteArrayInputStream(plainText.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            cryptoProvider.encryptEncStream((RSAPublicKey)rsaKeyPair.getPublic(), inputStream, outputStream, fileExtension);
            cipherBytes = outputStream.toByteArray();
            cipherText = outputStream.toString(StandardCharsets.UTF_8.name());
            for (String s : plainText.split(" ")) {
                Assert.assertTrue("Cipher text should not contain any of the plain text", !cipherText.contains(s));
            }
        }

        try(InputStream inputStream = new ByteArrayInputStream(cipherBytes);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            String decExtension = cryptoProvider.decryptEncStream((RSAPrivateKey)rsaKeyPair.getPrivate(), inputStream, outputStream);
            String decPlainText = outputStream.toString(StandardCharsets.UTF_8.name());
            Assert.assertEquals("Decrypted text should be the same as the plain text", plainText, decPlainText);
            Assert.assertEquals("Decrypted extension should be the same as the plain extension", fileExtension, decExtension);
        }
    }

    @Test
    public void logTest() throws IOException, NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, MalformedLogFileException {
        String plainText = "The quick brown fox jumped over the lazy dog";
        String expectedPlainText = plainText + newLine + newLine;
        byte[] cipherBytes;

        cipherBytes = encryptNextLog(plainText);

        try(InputStream inputStream = new ByteArrayInputStream(cipherBytes);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(outputStream, true, StandardCharsets.UTF_8.name())) {
            cryptoProvider.decryptLogStream((RSAPrivateKey)rsaKeyPair.getPrivate(), inputStream, printStream);
            String decPlainText = outputStream.toString(StandardCharsets.UTF_8.name());
            Assert.assertEquals("Decrypted text should be the same as the plain text", expectedPlainText, decPlainText);
        }
    }

    @Test
    public void getNextLogBlockTest() throws IOException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException,
            MalformedLogFileException {
        String plainText1 = "The quick brown fox jumped over the lazy dog";
        String plainText2 = "The smaller red squirrel leaped above the sleepy cat";
        byte[] cipherBytes1;
        byte[] cipherBytes2;
        byte[] allBytes;

        cipherBytes1 = encryptNextLog(plainText1);
        cipherBytes2 = encryptNextLog(plainText2);
        allBytes = ArrayUtils.addAll(cipherBytes1, cipherBytes2);

        try(InputStream inputStream = new ByteArrayInputStream(allBytes)) {
            assertNextLog(inputStream, plainText1, cipherBytes1);
            assertNextLog(inputStream, plainText2, cipherBytes2);
        }
    }

    private void assertNextLog(InputStream inputStream, String expectedPlainText, byte[] expectedBytes)
            throws IOException, InvalidAlgorithmParameterException, MalformedLogFileException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidKeyException {
        byte[] lc = new byte[CryptoProvider.LENGTH_CODE_SIZE];
        Assert.assertTrue(cryptoProvider.logHasNext(inputStream, lc));
        LogDataBlocksAndText ldb = cryptoProvider.getNextLogTextWithDataBlocks(inputStream,
                (RSAPrivateKey)rsaKeyPair.getPrivate(), lc);
        Assert.assertEquals("Log should have correct plain text", expectedPlainText, ldb.getEntryText());
        Assert.assertThat("All cipher bytes should be in the ldb", expectedBytes,
                equalTo(ldb.getLogDataBlocks().getAsOneBlock()));
    }

    private byte[] encryptNextLog(String plainText) throws NoSuchPaddingException,
            InvalidKeyException, NoSuchAlgorithmException, IOException, BadPaddingException, IllegalBlockSizeException,
            InvalidAlgorithmParameterException {
        byte[] cipherBytes;
        try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            cryptoProvider.encryptLogStream((RSAPublicKey) rsaKeyPair.getPublic(), plainText, outputStream);
            cipherBytes = outputStream.toByteArray();
            String cipherText = outputStream.toString(StandardCharsets.UTF_8.name());
            for (String s : plainText.split(" ")) {
                Assert.assertTrue("Cipher text should not contain any of the plain text", !cipherText.contains(s));
            }
        }
        return cipherBytes;
    }

}






















