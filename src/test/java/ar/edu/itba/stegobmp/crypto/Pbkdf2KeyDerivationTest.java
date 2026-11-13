package ar.edu.itba.stegobmp.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Pbkdf2KeyDerivationTest {

    private static final byte[] ZERO_SALT = new byte[8];
    private final Pbkdf2KeyDerivation derivation = new Pbkdf2KeyDerivation();

    /** PBKDF2-HMAC-SHA256 de RFC 8018 §5.2 escrito a mano sobre {@link Mac}, independiente de SunJCE PBKDF2. */
    static byte[] referencePbkdf2(byte[] password, byte[] salt, int iterations, int dkLen) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        // HMAC rellena la clave con ceros hasta el tamaño de bloque: la clave vacía equivale a un único byte 0
        mac.init(new SecretKeySpec(password.length == 0 ? new byte[1] : password, "HmacSHA256"));
        byte[] out = new byte[dkLen];
        int blocks = (dkLen + 31) / 32;
        for (int block = 1; block <= blocks; block++) {
            mac.update(salt);
            mac.update(new byte[] {(byte) (block >>> 24), (byte) (block >>> 16), (byte) (block >>> 8), (byte) block});
            byte[] u = mac.doFinal();
            byte[] t = u.clone();
            for (int i = 1; i < iterations; i++) {
                u = mac.doFinal(u);
                for (int j = 0; j < t.length; j++) {
                    t[j] ^= u[j];
                }
            }
            System.arraycopy(t, 0, out, (block - 1) * 32, Math.min(32, dkLen - (block - 1) * 32));
        }
        return out;
    }

    private static byte[] split(DerivedKey key) {
        return CryptoTestData.concat(key.keyBytes(), key.ivBytes());
    }

    @Test
    void referenceImplementationMatchesRfc7914Vectors() throws Exception {
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc"
                        + "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
                CryptoTestData.hex(referencePbkdf2("passwd".getBytes(StandardCharsets.US_ASCII),
                        "salt".getBytes(StandardCharsets.US_ASCII), 1, 64)));
        assertEquals("4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56"
                        + "a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d",
                CryptoTestData.hex(referencePbkdf2("Password".getBytes(StandardCharsets.US_ASCII),
                        "NaCl".getBytes(StandardCharsets.US_ASCII), 80000, 64)));
    }

    @Test
    void sunJcePbkdf2MatchesRfc7914Vector() throws Exception {
        byte[] dk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec("passwd".toCharArray(), "salt".getBytes(StandardCharsets.US_ASCII), 1, 512))
                .getEncoded();
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc"
                + "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783", CryptoTestData.hex(dk));
    }

    @Test
    void ocultoDerivationMatchesTheValueObservedInJavaAndOpenSsl() throws Exception {
        String dk32 = "e5fb03e4abd3d239cbb4f0aaa357240120efcd7a8c7d9986b0bf3089eff2d9b7";
        assertEquals(dk32, CryptoTestData.hex(referencePbkdf2("oculto".getBytes(StandardCharsets.UTF_8), ZERO_SALT, 10_000, 32)));
        try (DerivedKey key = derivation.derive("oculto".toCharArray(), new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC))) {
            assertEquals(dk32, CryptoTestData.hex(split(key)));
        }
    }

    @Test
    void emptyPasswordDerivationMatchesTheValueObservedInJavaAndOpenSsl() throws Exception {
        try (DerivedKey key = derivation.derive(new char[0], new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC))) {
            assertEquals("fa8d81b6d185646b749b94929c82cd460acf1f0c944b508114bb18c0cedc3e16", CryptoTestData.hex(split(key)));
        }
    }

    static Stream<Arguments> openSslPBannerValues() {
        // Salida de `openssl enc -<cifrador> -pbkdf2 -iter 10000 -md sha256 -S 0000000000000000 -pass pass:oculto -P`
        // (OpenSSL 3.6.2 y LibreSSL 3.3.6 coinciden). Ver CRYPTO_DESIGN.md §4.1.
        String k16 = "E5FB03E4ABD3D239CBB4F0AAA3572401";
        String k24 = "E5FB03E4ABD3D239CBB4F0AAA357240120EFCD7A8C7D9986";
        String k32 = "E5FB03E4ABD3D239CBB4F0AAA357240120EFCD7A8C7D9986B0BF3089EFF2D9B7";
        return Stream.of(
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.ECB, k16, ""),
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.CBC, k16, "20EFCD7A8C7D9986B0BF3089EFF2D9B7"),
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.CFB8, k16, "20EFCD7A8C7D9986B0BF3089EFF2D9B7"),
                Arguments.of(CryptoAlgorithm.AES_192, CryptoMode.CBC, k24, "B0BF3089EFF2D9B74D908F992BE5FDDF"),
                Arguments.of(CryptoAlgorithm.AES_256, CryptoMode.CBC, k32, "4D908F992BE5FDDF985C7B0C567655B6"),
                Arguments.of(CryptoAlgorithm.TRIPLE_DES, CryptoMode.ECB, k24, ""),
                Arguments.of(CryptoAlgorithm.TRIPLE_DES, CryptoMode.CBC, k24, "B0BF3089EFF2D9B7"),
                Arguments.of(CryptoAlgorithm.TRIPLE_DES, CryptoMode.OFB, k24, "B0BF3089EFF2D9B7"));
    }

    @ParameterizedTest
    @MethodSource("openSslPBannerValues")
    void keyAndIvSplitMatchesOpenSsl(CryptoAlgorithm algorithm, CryptoMode mode, String key, String iv) throws Exception {
        try (DerivedKey derived = derivation.derive("oculto".toCharArray(), new CipherSpec(algorithm, mode))) {
            assertEquals(key.toLowerCase(), CryptoTestData.hex(derived.keyBytes()));
            assertEquals(iv.toLowerCase(), CryptoTestData.hex(derived.ivBytes()));
        }
    }

    @Test
    void keyIsAPrefixOfTheLongerDerivationSoEcbAndCbcShareIt() throws Exception {
        for (CryptoAlgorithm algorithm : CryptoAlgorithm.values()) {
            try (DerivedKey ecb = derivation.derive("clave".toCharArray(), new CipherSpec(algorithm, CryptoMode.ECB));
                    DerivedKey cbc = derivation.derive("clave".toCharArray(), new CipherSpec(algorithm, CryptoMode.CBC))) {
                assertArrayEquals(ecb.keyBytes(), cbc.keyBytes(), algorithm.name());
                assertEquals(0, ecb.ivLength());
                assertEquals(algorithm.blockBytes(), cbc.ivLength());
            }
        }
    }

    @Test
    void lengthsFollowTheSpecForAllCombinations() throws Exception {
        for (CipherSpec spec : CryptoTestData.allSpecs()) {
            try (DerivedKey key = derivation.derive("x".toCharArray(), spec)) {
                assertEquals(spec.algorithm().keyBytes(), key.keyLength(), spec.description());
                assertEquals(spec.ivBytes(), key.ivLength(), spec.description());
            }
        }
    }

    static Stream<String> passwords() {
        return Stream.of("a", "x".repeat(63), "x".repeat(64), "x".repeat(65), "y".repeat(200),
                "con espacios  y\ttab", "contraseña", "€uro", "日本語のパスワード", "emoji-\uD83D\uDE00-fin",
                CryptoTestData.NON_ASCII_PASSWORD, CryptoTestData.LONG_PASSWORD);
    }

    @ParameterizedTest
    @MethodSource("passwords")
    void derivationMatchesAnIndependentPbkdf2OfTheUtf8Bytes(String password) throws Exception {
        for (CipherSpec spec : new CipherSpec[] {new CipherSpec(CryptoAlgorithm.AES_256, CryptoMode.CBC),
                new CipherSpec(CryptoAlgorithm.TRIPLE_DES, CryptoMode.ECB)}) {
            byte[] expected = referencePbkdf2(password.getBytes(StandardCharsets.UTF_8), ZERO_SALT, 10_000, spec.derivedBytes());
            try (DerivedKey key = derivation.derive(password.toCharArray(), spec)) {
                assertArrayEquals(expected, split(key), spec.description());
            }
        }
    }

    @Test
    void unicodeIsNotNormalised() throws Exception {
        String nfc = Normalizer.normalize("contraseña", Normalizer.Form.NFC);
        String nfd = Normalizer.normalize("contraseña", Normalizer.Form.NFD);
        assertNotEquals(nfc, nfd);
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        try (DerivedKey a = derivation.derive(nfc.toCharArray(), spec); DerivedKey b = derivation.derive(nfd.toCharArray(), spec)) {
            assertFalse(Arrays.equals(a.keyBytes(), b.keyBytes()));
        }
    }

    @Test
    void differentPasswordsGiveDifferentKeys() throws Exception {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        try (DerivedKey a = derivation.derive("oculto".toCharArray(), spec); DerivedKey b = derivation.derive("oculta".toCharArray(), spec)) {
            assertFalse(Arrays.equals(a.keyBytes(), b.keyBytes()));
        }
    }

    @Test
    void nullArgumentsAreRejected() {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        assertThrows(InvalidCryptoParametersException.class, () -> derivation.derive(null, spec));
        assertThrows(InvalidCryptoParametersException.class, () -> derivation.derive("x".toCharArray(), null));
    }

    @Test
    void unpairedSurrogatesAreRejectedAndValidPairsAccepted() throws Exception {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        for (String bad : new String[] {"\uD83D", "abc\uDE00", "\uD83D\uD83D", "\uDE00\uD83D", "\uD83Dx"}) {
            InvalidCryptoParametersException e = assertThrows(InvalidCryptoParametersException.class,
                    () -> derivation.derive(bad.toCharArray(), spec), bad);
            assertTrue(e.getMessage().contains("UTF-8"));
        }
        try (DerivedKey key = derivation.derive("\uD83D\uDE00".toCharArray(), spec)) {
            byte[] expected = referencePbkdf2("\uD83D\uDE00".getBytes(StandardCharsets.UTF_8), ZERO_SALT, 10_000, 32);
            assertArrayEquals(expected, split(key));
        }
    }

    @Test
    void callerPasswordArrayIsNotModified() throws Exception {
        char[] password = "oculto".toCharArray();
        derivation.derive(password, new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.ECB)).close();
        assertArrayEquals("oculto".toCharArray(), password);
    }

    @Test
    void errorMessagesNeverContainThePassword() {
        String secret = "S3cr3t-\uD83D";
        InvalidCryptoParametersException e = assertThrows(InvalidCryptoParametersException.class,
                () -> derivation.derive(secret.toCharArray(), new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC)));
        assertFalse(e.getMessage().contains("S3cr3t"));
    }
}
