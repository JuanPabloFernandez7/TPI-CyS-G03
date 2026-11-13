package ar.edu.itba.stegobmp.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Interoperabilidad Java ↔ {@code openssl enc}. Se omite si no hay openssl; falla si existe y se comporta de forma inesperada.
 * Para usar otro binario: {@code -Dstegobmp.openssl=/usr/bin/openssl}.
 */
class OpenSslInteropIT {

    private static final int[] LENGTHS = {0, 1, 7, 8, 15, 16, 17, 423};
    private static OpenSslHarness openssl;

    @BeforeAll
    static void detectOpenSsl() {
        var detected = OpenSslHarness.detect();
        assumeTrue(detected.isPresent(), "openssl no está instalado: se omiten las pruebas de interoperabilidad");
        openssl = detected.get();
        System.out.println("[OpenSslInteropIT] " + openssl.version() + " dialecto=" + openssl.dialect());
    }

    static Stream<Arguments> specsAndPasswords() {
        List<String> passwords = List.of(CryptoTestData.ASCII_PASSWORD, CryptoTestData.NON_ASCII_PASSWORD,
                CryptoTestData.LONG_PASSWORD, "");
        return CryptoTestData.allSpecs().stream().flatMap(spec -> passwords.stream().map(pw -> Arguments.of(spec, pw)));
    }

    static Stream<CipherSpec> specs() {
        return CryptoTestData.allSpecs().stream();
    }

    @ParameterizedTest(name = "{0} / pw={1}")
    @MethodSource("specsAndPasswords")
    void javaAndOpenSslProduceAndAcceptTheSameCiphertext(CipherSpec spec, String password) throws Exception {
        try (PasswordPayloadCipher java = new PasswordPayloadCipher(spec, password.toCharArray())) {
            for (int length : LENGTHS) {
                byte[] plaintext = CryptoTestData.seeded(length + 99L, length);
                String label = spec.description() + " L=" + length;

                byte[] fromJava = java.encrypt(plaintext);
                byte[] fromOpenSsl = openssl.encrypt(spec, password, plaintext);
                assertArrayEquals(fromOpenSsl, fromJava, "cifrado idéntico " + label);

                assertArrayEquals(plaintext, openssl.decrypt(spec, password, fromJava), "Java -> OpenSSL " + label);
                assertArrayEquals(plaintext, java.decrypt(fromOpenSsl), "OpenSSL -> Java " + label);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("specs")
    void derivedKeyAndIvMatchOpenSslP(CipherSpec spec) throws Exception {
        for (String password : new String[] {CryptoTestData.ASCII_PASSWORD, CryptoTestData.NON_ASCII_PASSWORD, ""}) {
            byte[][] expected = openssl.derive(spec, password);
            try (DerivedKey key = new Pbkdf2KeyDerivation().derive(password.toCharArray(), spec)) {
                assertArrayEquals(expected[0], key.keyBytes(), "clave " + spec.description());
                assertArrayEquals(expected[1], key.ivBytes(), "IV " + spec.description());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("specs")
    void explicitKeyAndIvEncryptionMatchesSymmetricCipher(CipherSpec spec) throws Exception {
        byte[] key = CryptoTestData.seeded(11, spec.algorithm().keyBytes());
        byte[] iv = spec.mode().usesIv() ? CryptoTestData.seeded(12, spec.ivBytes()) : new byte[0];
        byte[] plaintext = CryptoTestData.seeded(13, 100);
        try (DerivedKey derived = DerivedKey.of(key, iv)) {
            assertArrayEquals(openssl.encryptWithKey(spec, key, iv, plaintext), SymmetricCipher.encrypt(spec, derived, plaintext),
                    spec.description());
        }
    }

    @Test
    void negativeControlsDetectDifferentParameters() throws Exception {
        byte[] plaintext = CryptoTestData.pattern(64);
        CipherSpec cfb8 = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CFB8);
        try (PasswordPayloadCipher ours = new PasswordPayloadCipher(cfb8, "oculto".toCharArray())) {
            byte[] mine = ours.encrypt(plaintext);
            assertArrayEquals(mine, openssl.encryptNamed("aes-128-cfb8", "oculto", plaintext, OpenSslHarness.ZERO_SALT, 64));
            assertFalse(Arrays.equals(mine, openssl.encryptNamed("aes-128-cfb", "oculto", plaintext, OpenSslHarness.ZERO_SALT, 64)),
                    "CFB de 128 bits no debe coincidir con CFB8");
            assertFalse(Arrays.equals(mine, openssl.encryptNamed("aes-128-ofb", "oculto", plaintext, OpenSslHarness.ZERO_SALT, 64)));
            assertFalse(Arrays.equals(mine, openssl.encryptNamed("aes-128-cfb8", "oculta", plaintext, OpenSslHarness.ZERO_SALT, 64)));
            assertFalse(Arrays.equals(mine, openssl.encryptNamed("aes-128-cfb8", "oculto", plaintext, "0000000000000001", 64)),
                    "una sal distinta debe cambiar el resultado");
        }
    }
}
