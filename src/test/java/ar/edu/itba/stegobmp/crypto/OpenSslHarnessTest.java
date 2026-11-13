package ar.edu.itba.stegobmp.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

/** Prueba el clasificador del harness con salidas sintéticas; no requiere OpenSSL. */
class OpenSslHarnessTest {

    private static final byte[] MAGIC = "Salted__".getBytes(StandardCharsets.US_ASCII);
    private static final String Z = OpenSslHarness.ZERO_SALT;

    @Test
    void acceptsOutputWithoutHeader() {
        byte[] out = new byte[32];
        out[0] = 1;
        assertEquals(OpenSslHarness.Dialect.NO_HEADER, OpenSslHarness.classify(out, 32, Z, "test"));
        assertArrayEquals(out, OpenSslHarness.stripHeader(out, 32, Z, "test"));
    }

    @Test
    void acceptsAndStripsSaltedHeaderWithTheExpectedSalt() {
        byte[] body = CryptoTestData.seeded(1, 32);
        byte[] out = CryptoTestData.concat(MAGIC, new byte[8], body);
        assertEquals(OpenSslHarness.Dialect.SALTED_HEADER, OpenSslHarness.classify(out, 32, Z, "test"));
        assertArrayEquals(body, OpenSslHarness.stripHeader(out, 32, Z, "test"));
    }

    @Test
    void rejectsWrongMagicWrongSaltWrongLengthAndFalseHeader() {
        byte[] body = new byte[32];
        byte[] wrongMagic = CryptoTestData.concat("Salted_X".getBytes(StandardCharsets.US_ASCII), new byte[8], body);
        byte[] wrongSalt = CryptoTestData.concat(MAGIC, new byte[] {0, 0, 0, 0, 0, 0, 0, 1}, body);
        byte[] shortHeader = CryptoTestData.concat(MAGIC, new byte[8], new byte[16]);
        byte[] headerWithoutBodyGrowth = CryptoTestData.concat(MAGIC, new byte[24]);
        byte[] tooLong = new byte[49];
        for (byte[] bad : new byte[][] {wrongMagic, wrongSalt, shortHeader, tooLong, new byte[31], headerWithoutBodyGrowth}) {
            assertThrows(AssertionFailedError.class, () -> OpenSslHarness.classify(bad, 32, Z, "test"));
        }
    }

    @Test
    void cipherNamesMatchOpenSsl() {
        assertEquals("aes-128-cfb8", OpenSslHarness.cipherName(new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CFB8)));
        assertEquals("aes-256-ofb", OpenSslHarness.cipherName(new CipherSpec(CryptoAlgorithm.AES_256, CryptoMode.OFB)));
        assertEquals("des-ede3", OpenSslHarness.cipherName(new CipherSpec(CryptoAlgorithm.TRIPLE_DES, CryptoMode.ECB)));
        assertEquals("des-ede3-cbc", OpenSslHarness.cipherName(new CipherSpec(CryptoAlgorithm.TRIPLE_DES, CryptoMode.CBC)));
    }
}
