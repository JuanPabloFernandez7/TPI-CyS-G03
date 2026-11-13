package ar.edu.itba.stegobmp.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PasswordPayloadCipherTest {

    static Stream<CipherSpec> specs() {
        return CryptoTestData.allSpecs().stream();
    }

    static Stream<String> passwords() {
        return Stream.of(CryptoTestData.ASCII_PASSWORD, CryptoTestData.NON_ASCII_PASSWORD, CryptoTestData.LONG_PASSWORD, "",
                "emoji-\uD83D\uDE00", "a");
    }

    @ParameterizedTest
    @MethodSource("specs")
    void roundTripForAllPasswordsAndLengths(CipherSpec spec) throws Exception {
        for (String password : (Iterable<String>) passwords()::iterator) {
            try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(spec, password.toCharArray())) {
                for (int length : new int[] {0, 1, 423, spec.blockBytes() * 4}) {
                    byte[] plaintext = CryptoTestData.seeded(length + 7L, length);
                    byte[] ciphertext = cipher.encrypt(plaintext);
                    assertEquals(cipher.ciphertextLength(length), ciphertext.length);
                    assertArrayEquals(plaintext, cipher.decrypt(ciphertext), spec.description());
                }
            }
        }
    }

    @Test
    void outputIsDeterministicAndDependsOnPasswordAlgorithmAndMode() throws Exception {
        byte[] data = CryptoTestData.pattern(100);
        CipherSpec base = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        byte[] reference = encrypt(base, "oculto", data);
        assertArrayEquals(reference, encrypt(base, "oculto", data));
        assertFalse(Arrays.equals(reference, encrypt(base, "oculta", data)));
        assertFalse(Arrays.equals(reference, encrypt(new CipherSpec(CryptoAlgorithm.AES_256, CryptoMode.CBC), "oculto", data)));
        assertFalse(Arrays.equals(reference, encrypt(new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.ECB), "oculto", data)));
    }

    @Test
    void worksThroughThePayloadCipherInterface() throws Exception {
        PayloadCipher cipher = new PasswordPayloadCipher(new CipherSpec(CryptoAlgorithm.TRIPLE_DES, CryptoMode.CBC), "oculto".toCharArray());
        byte[] data = CryptoTestData.pattern(423);
        byte[] ciphertext = cipher.encrypt(data);
        assertEquals(424, ciphertext.length);
        assertArrayEquals(data, cipher.decrypt(ciphertext));
    }

    @Test
    void closedCipherCannotBeUsed() throws Exception {
        PasswordPayloadCipher cipher = new PasswordPayloadCipher(new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC), "x".toCharArray());
        cipher.close();
        assertThrows(IllegalStateException.class, () -> cipher.encrypt(new byte[1]));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(new byte[16]));
        cipher.close();
    }

    @Test
    void constructorCopiesThePassword() throws Exception {
        char[] password = "oculto".toCharArray();
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        byte[] expected = encrypt(spec, "oculto", new byte[10]);
        try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(spec, password)) {
            Arrays.fill(password, 'z');
            assertArrayEquals(expected, cipher.encrypt(new byte[10]));
        }
    }

    @Test
    void nullArgumentsAreRejected() throws Exception {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        assertThrows(InvalidCryptoParametersException.class, () -> new PasswordPayloadCipher(null, "x".toCharArray()));
        assertThrows(InvalidCryptoParametersException.class, () -> new PasswordPayloadCipher(spec, null));
        assertThrows(InvalidCryptoParametersException.class, () -> new PasswordPayloadCipher(spec, "x".toCharArray(), null));
        try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(spec, "x".toCharArray())) {
            assertThrows(InvalidCryptoParametersException.class, () -> cipher.encrypt(null));
            assertThrows(InvalidCryptoParametersException.class, () -> cipher.decrypt(null));
        }
    }

    @Test
    void emptyCiphertextDependsOnTheMode() throws Exception {
        try (PasswordPayloadCipher cbc = new PasswordPayloadCipher(new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC), "x".toCharArray());
                PasswordPayloadCipher cfb8 = new PasswordPayloadCipher(new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CFB8), "x".toCharArray())) {
            assertThrows(InvalidCiphertextException.class, () -> cbc.decrypt(new byte[0]));
            assertEquals(0, cfb8.decrypt(new byte[0]).length);
        }
    }

    @Test
    void wrongPasswordIsMostlyDetectedByPaddingInEcbAndCbc() throws Exception {
        for (CryptoMode mode : new CryptoMode[] {CryptoMode.ECB, CryptoMode.CBC}) {
            CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, mode);
            byte[] data = CryptoTestData.pattern(50);
            byte[] ciphertext = encrypt(spec, "correcta", data);
            int detected = 0;
            for (int i = 0; i < 100; i++) {
                try (PasswordPayloadCipher wrong = new PasswordPayloadCipher(spec, ("incorrecta-" + i).toCharArray())) {
                    try {
                        assertFalse(Arrays.equals(data, wrong.decrypt(ciphertext)));
                    } catch (InvalidPaddingException e) {
                        detected++;
                    }
                }
            }
            assertTrue(detected >= 90, mode + " detectadas: " + detected);
        }
    }

    @Test
    void wrongPasswordIsNeverDetectedInStreamModes() throws Exception {
        for (CryptoMode mode : new CryptoMode[] {CryptoMode.CFB8, CryptoMode.OFB}) {
            CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, mode);
            byte[] data = CryptoTestData.pattern(50);
            byte[] ciphertext = encrypt(spec, "correcta", data);
            for (int i = 0; i < 20; i++) {
                try (PasswordPayloadCipher wrong = new PasswordPayloadCipher(spec, ("mala-" + i).toCharArray())) {
                    byte[] result = wrong.decrypt(ciphertext);
                    assertEquals(data.length, result.length);
                    assertFalse(Arrays.equals(data, result));
                }
            }
        }
    }

    @Test
    void oneInstanceCanBeSharedAcrossThreads() throws Exception {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_192, CryptoMode.CFB8);
        try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(spec, "oculto".toCharArray())) {
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                List<Future<Boolean>> futures = new ArrayList<>();
                for (int t = 0; t < 8; t++) {
                    final long seed = t;
                    Callable<Boolean> task = () -> {
                        for (int i = 0; i < 5; i++) {
                            byte[] data = CryptoTestData.seeded(seed * 100 + i, 100 + i);
                            if (!Arrays.equals(data, cipher.decrypt(cipher.encrypt(data)))) {
                                return false;
                            }
                        }
                        return true;
                    };
                    futures.add(pool.submit(task));
                }
                for (Future<Boolean> f : futures) {
                    assertTrue(f.get());
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void toStringAndMessagesDoNotLeakThePassword() throws Exception {
        String secret = "Sup3rS3cret";
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(spec, secret.toCharArray())) {
            assertFalse(cipher.toString().contains(secret));
            Exception e = assertThrows(InvalidCiphertextException.class, () -> cipher.decrypt(new byte[3]));
            assertFalse(e.getMessage().contains(secret));
        }
    }

    @Test
    void customKeyDerivationIsUsedAndItsKeyClosed() throws Exception {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        int[] calls = {0};
        KeyDerivation fake = (password, s) -> {
            calls[0]++;
            return DerivedKey.of(new byte[16], new byte[16]);
        };
        try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(spec, "ignorada".toCharArray(), fake)) {
            byte[] data = CryptoTestData.pattern(20);
            assertArrayEquals(data, cipher.decrypt(cipher.encrypt(data)));
            assertEquals(2, calls[0]);
        }
    }

    @Test
    void derivationFailuresPropagateAsCryptoExceptions() throws Exception {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        KeyDerivation failing = (password, s) -> {
            throw new KeyDerivationException("fallo simulado", null);
        };
        try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(spec, "x".toCharArray(), failing)) {
            assertNotNull(assertThrows(KeyDerivationException.class, () -> cipher.encrypt(new byte[1])));
            assertNotEquals(null, assertThrows(KeyDerivationException.class, () -> cipher.decrypt(new byte[16])));
        }
    }

    private static byte[] encrypt(CipherSpec spec, String password, byte[] data) throws Exception {
        try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(spec, password.toCharArray())) {
            return cipher.encrypt(data);
        }
    }
}
