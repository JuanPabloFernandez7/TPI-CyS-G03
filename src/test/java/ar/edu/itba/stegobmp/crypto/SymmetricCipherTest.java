package ar.edu.itba.stegobmp.crypto;

import static ar.edu.itba.stegobmp.crypto.CryptoTestData.concat;
import static ar.edu.itba.stegobmp.crypto.CryptoTestData.hex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SymmetricCipherTest {

    // NIST SP 800-38A, Apéndice F: clave, IV y plaintext de cuatro bloques (los valores se cotejaron además con OpenSSL).
    private static final String PT = "6bc1bee22e409f96e93d7e117393172a ae2d8a571e03ac9c9eb76fac45af8e51"
            + " 30c81c46a35ce411e5fbc1191a0a52ef f69f2445df4f9b17ad2b417be66c3710";
    private static final String IV = "000102030405060708090a0b0c0d0e0f";
    private static final String K128 = "2b7e151628aed2a6abf7158809cf4f3c";
    private static final String K192 = "8e73b0f7da0e6452c810f32b809079e562f8ead2522c6b7b";
    private static final String K256 = "603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4";

    static Stream<Arguments> nistVectors() {
        return Stream.of(
                // F.1 ECB
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.ECB, K128, PT,
                        "3ad77bb40d7a3660a89ecaf32466ef97 f5d3d58503b9699de785895a96fdbaaf 43b1cd7f598ece23881b00e3ed030688 7b0c785e27e8ad3f8223207104725dd4"),
                Arguments.of(CryptoAlgorithm.AES_192, CryptoMode.ECB, K192, PT,
                        "bd334f1d6e45f25ff712a214571fa5cc 974104846d0ad3ad7734ecb3ecee4eef ef7afd2270e2e60adce0ba2face6444e 9a4b41ba738d6c72fb16691603c18e0e"),
                Arguments.of(CryptoAlgorithm.AES_256, CryptoMode.ECB, K256, PT,
                        "f3eed1bdb5d2a03c064b5a7e3db181f8 591ccb10d410ed26dc5ba74a31362870 b6ed21b99ca6f4f9f153e7b1beafed1d 23304b7a39f9f3ff067d8d8f9e24ecc7"),
                // F.2 CBC
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.CBC, K128, PT,
                        "7649abac8119b246cee98e9b12e9197d 5086cb9b507219ee95db113a917678b2 73bed6b8e3c1743b7116e69e22229516 3ff1caa1681fac09120eca307586e1a7"),
                Arguments.of(CryptoAlgorithm.AES_192, CryptoMode.CBC, K192, PT,
                        "4f021db243bc633d7178183a9fa071e8 b4d9ada9ad7dedf4e5e738763f69145a 571b242012fb7ae07fa9baac3df102e0 08b0e27988598881d920a9e64f5615cd"),
                Arguments.of(CryptoAlgorithm.AES_256, CryptoMode.CBC, K256, PT,
                        "f58c4c04d6e5f1ba779eabfb5f7bfbd6 9cfc4e967edb808d679f777bc6702c7d 39f23369a9d9bacfa530e26304231461 b2eb05e2c39be9fcda6c19078c6a9d1b"),
                // F.3.7-F.3.9 CFB8 (18 bytes de plaintext)
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.CFB8, K128, "6bc1bee22e409f96e93d7e117393172aae2d",
                        "3b79424c9c0dd436bace9e0ed4586a4f32b9"),
                Arguments.of(CryptoAlgorithm.AES_192, CryptoMode.CFB8, K192, "6bc1bee22e409f96e93d7e117393172aae2d",
                        "cda2521ef0a905ca44cd057cbf0d47a0678a"),
                Arguments.of(CryptoAlgorithm.AES_256, CryptoMode.CFB8, K256, "6bc1bee22e409f96e93d7e117393172aae2d",
                        "dc1f1a8520a64db55fcc8ac554844e889700"),
                // F.4 OFB
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.OFB, K128, PT,
                        "3b3fd92eb72dad20333449f8e83cfb4a 7789508d16918f03f53c52dac54ed825 9740051e9c5fecf64344f7a82260edcc 304c6528f659c77866a510d9c1d6ae5e"),
                Arguments.of(CryptoAlgorithm.AES_192, CryptoMode.OFB, K192, PT,
                        "cdc80d6fddf18cab34c25909c99a4174 fcc28b8d4c63837c09e81700c1100401 8d9a9aeac0f6596f559c6d4daf59a5f2 6d9f200857ca6c3e9cac524bd9acc92a"),
                Arguments.of(CryptoAlgorithm.AES_256, CryptoMode.OFB, K256, PT,
                        "dc7e84bfda79164b7ecd8486985d3860 4febdc6740d20b3ac88f6ad82a4fb08d 71ab47a086e86eedf39d1c5bba97c408 0126141d67f37be8538f5a8be740e484"));
    }

    @ParameterizedTest
    @MethodSource("nistVectors")
    void nistVectorsEncryptAndDecrypt(CryptoAlgorithm algorithm, CryptoMode mode, String key, String plaintext, String expected)
            throws Exception {
        CipherSpec spec = new CipherSpec(algorithm, mode);
        byte[] pt = hex(plaintext);
        byte[] expectedCt = hex(expected);
        try (DerivedKey derived = DerivedKey.of(hex(key), mode.usesIv() ? hex(IV) : new byte[0])) {
            byte[] ct = SymmetricCipher.encrypt(spec, derived, pt);
            if (mode.isPadded()) {
                // el vector NIST no lleva padding: con PKCS5 se agrega un bloque completo al final
                assertEquals(pt.length + algorithm.blockBytes(), ct.length);
                assertArrayEquals(expectedCt, Arrays.copyOf(ct, pt.length));
            } else {
                assertArrayEquals(expectedCt, ct);
            }
            assertArrayEquals(pt, SymmetricCipher.decrypt(spec, derived, ct));
            if (!mode.isPadded()) {
                assertArrayEquals(pt, SymmetricCipher.decrypt(spec, derived, expectedCt));
            }
        }
    }

    @Test
    void paddedNistVectorsDecryptWhenTheStandardPaddingBlockIsAppended() throws Exception {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        try (DerivedKey derived = DerivedKey.of(hex(K128), hex(IV))) {
            byte[] full = SymmetricCipher.encrypt(spec, derived, hex(PT));
            byte[] nist = hex("7649abac8119b246cee98e9b12e9197d 5086cb9b507219ee95db113a917678b2"
                    + " 73bed6b8e3c1743b7116e69e222295163ff1caa1681fac09120eca307586e1a7");
            assertArrayEquals(nist, Arrays.copyOf(full, 64));
        }
    }

    static Stream<CipherSpec> allSpecs() {
        return CryptoTestData.allSpecs().stream();
    }

    @ParameterizedTest
    @MethodSource("allSpecs")
    void roundTripForManyLengths(CipherSpec spec) throws Exception {
        int block = spec.blockBytes();
        int[] lengths = {0, 1, 7, 8, 15, 16, 17, 31, 32, 33, block - 1, block, block + 1, 3 * block, 423, 4096};
        try (DerivedKey key = CryptoTestData.fixedKey(spec)) {
            for (int length : lengths) {
                byte[] plaintext = CryptoTestData.seeded(length * 31L + 5, length);
                byte[] original = plaintext.clone();
                byte[] ciphertext = SymmetricCipher.encrypt(spec, key, plaintext);
                assertArrayEquals(original, plaintext, "la entrada no debe modificarse");
                assertEquals(spec.ciphertextLength(length), ciphertext.length, spec.description() + " P=" + length);
                byte[] copy = ciphertext.clone();
                assertArrayEquals(plaintext, SymmetricCipher.decrypt(spec, key, ciphertext), spec.description() + " P=" + length);
                assertArrayEquals(copy, ciphertext, "la entrada no debe modificarse");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allSpecs")
    void emptyPlaintextRoundTrips(CipherSpec spec) throws Exception {
        try (DerivedKey key = CryptoTestData.fixedKey(spec)) {
            byte[] ciphertext = SymmetricCipher.encrypt(spec, key, new byte[0]);
            assertEquals(spec.isPadded() ? spec.blockBytes() : 0, ciphertext.length);
            assertEquals(0, SymmetricCipher.decrypt(spec, key, ciphertext).length);
        }
    }

    @Test
    void blockMultipleGetsAFullPaddingBlock() throws Exception {
        for (CipherSpec spec : CryptoTestData.allSpecs()) {
            if (!spec.isPadded()) {
                continue;
            }
            try (DerivedKey key = CryptoTestData.fixedKey(spec)) {
                int block = spec.blockBytes();
                assertEquals(2 * block, SymmetricCipher.encrypt(spec, key, new byte[block]).length);
                assertEquals(4 * block, SymmetricCipher.encrypt(spec, key, new byte[3 * block]).length);
                assertEquals(block, SymmetricCipher.encrypt(spec, key, new byte[block - 1]).length);
            }
        }
    }

    @Test
    void consignaExample414ByteFileWithTxtExtensionIs423Bytes() throws Exception {
        byte[] data = CryptoTestData.seeded(414, 414);
        byte[] plaintext = concat(new byte[] {0, 0, 0x01, (byte) 0x9E}, data, ".txt\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(423, plaintext.length);
        for (CryptoAlgorithm algorithm : CryptoAlgorithm.values()) {
            for (CryptoMode mode : CryptoMode.values()) {
                CipherSpec spec = new CipherSpec(algorithm, mode);
                try (DerivedKey key = CryptoTestData.fixedKey(spec)) {
                    byte[] ciphertext = SymmetricCipher.encrypt(spec, key, plaintext);
                    long expected = !mode.isPadded() ? 423 : algorithm == CryptoAlgorithm.TRIPLE_DES ? 424 : 432;
                    assertEquals(expected, ciphertext.length, spec.description());
                    assertArrayEquals(plaintext, SymmetricCipher.decrypt(spec, key, ciphertext));
                }
            }
        }
    }

    static Stream<Arguments> invalidPaddingBlocks() {
        String aesBlockEndingIn00 = "00112233445566778899aabbccddee00";
        String aesBlockEndingIn11 = "00112233445566778899aabbccddee11";
        String aesInconsistent = "00112233445566778899aabbccdd0203";
        String desBlockEndingIn09 = "0011223344556609";
        String desInconsistent = "0011223344550203";
        return Stream.of(
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.CBC, aesBlockEndingIn00),
                Arguments.of(CryptoAlgorithm.AES_192, CryptoMode.CBC, aesBlockEndingIn11),
                Arguments.of(CryptoAlgorithm.AES_256, CryptoMode.CBC, aesInconsistent),
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.ECB, aesBlockEndingIn00),
                Arguments.of(CryptoAlgorithm.AES_256, CryptoMode.ECB, aesInconsistent),
                Arguments.of(CryptoAlgorithm.TRIPLE_DES, CryptoMode.CBC, desBlockEndingIn09),
                Arguments.of(CryptoAlgorithm.TRIPLE_DES, CryptoMode.ECB, desBlockEndingIn09),
                Arguments.of(CryptoAlgorithm.TRIPLE_DES, CryptoMode.CBC, desInconsistent));
    }

    @ParameterizedTest
    @MethodSource("invalidPaddingBlocks")
    void invalidPaddingIsDetected(CryptoAlgorithm algorithm, CryptoMode mode, String plaintextBlock) throws Exception {
        CipherSpec spec = new CipherSpec(algorithm, mode);
        try (DerivedKey key = CryptoTestData.fixedKey(spec)) {
            // se cifra el bloque tal cual con NoPadding para fabricar un ciphertext cuyo relleno es inválido
            Cipher raw = Cipher.getInstance(algorithm.jcaName() + "/" + mode.jcaMode() + "/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key.keyBytes(), algorithm.jcaName());
            if (mode.usesIv()) {
                raw.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(key.ivBytes()));
            } else {
                raw.init(Cipher.ENCRYPT_MODE, keySpec);
            }
            byte[] ciphertext = raw.doFinal(hex(plaintextBlock));
            assertThrows(InvalidPaddingException.class, () -> SymmetricCipher.decrypt(spec, key, ciphertext));
        }
    }

    @Test
    void invalidCiphertextLengthsAreRejectedInPaddedModes() throws Exception {
        for (CipherSpec spec : CryptoTestData.allSpecs()) {
            if (!spec.isPadded()) {
                continue;
            }
            int block = spec.blockBytes();
            try (DerivedKey key = CryptoTestData.fixedKey(spec)) {
                for (int length : new int[] {0, 1, block - 1, block + 1, 2 * block + 1}) {
                    InvalidCiphertextException e = assertThrows(InvalidCiphertextException.class,
                            () -> SymmetricCipher.decrypt(spec, key, new byte[length]), spec.description() + " L=" + length);
                    assertFalse(e.getMessage().isBlank());
                }
            }
        }
        // los casos exactos citados en el diseño
        CipherSpec aes = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        try (DerivedKey key = CryptoTestData.fixedKey(aes)) {
            assertTrue(assertThrows(InvalidCiphertextException.class, () -> SymmetricCipher.decrypt(aes, key, new byte[15]))
                    .getMessage().contains("15 bytes"));
            assertTrue(assertThrows(InvalidCiphertextException.class, () -> SymmetricCipher.decrypt(aes, key, new byte[0]))
                    .getMessage().contains("vacío"));
        }
    }

    @Test
    void streamModesAcceptAnyCiphertextLength() throws Exception {
        for (CipherSpec spec : CryptoTestData.allSpecs()) {
            if (spec.isPadded()) {
                continue;
            }
            try (DerivedKey key = CryptoTestData.fixedKey(spec)) {
                for (int length : new int[] {0, 1, 7, 9, 15, 17, 100}) {
                    assertEquals(length, SymmetricCipher.decrypt(spec, key, new byte[length]).length, spec.description());
                }
            }
        }
    }

    @Test
    void wrongKeyOrIvLengthIsRejected() throws Exception {
        CipherSpec cbc = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        CipherSpec ecb = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.ECB);
        CipherSpec des = new CipherSpec(CryptoAlgorithm.TRIPLE_DES, CryptoMode.CBC);
        byte[] data = new byte[16];
        try (DerivedKey shortKey = DerivedKey.of(new byte[15], new byte[16]);
                DerivedKey longKey = DerivedKey.of(new byte[17], new byte[16]);
                DerivedKey shortIv = DerivedKey.of(new byte[16], new byte[8]);
                DerivedKey desIvForAes = DerivedKey.of(new byte[16], new byte[8]);
                DerivedKey ecbWithIv = DerivedKey.of(new byte[16], new byte[16]);
                DerivedKey cbcWithoutIv = DerivedKey.of(new byte[16]);
                DerivedKey aesKeyForDes = DerivedKey.of(new byte[16], new byte[8])) {
            assertThrows(InvalidCryptoParametersException.class, () -> SymmetricCipher.encrypt(cbc, shortKey, data));
            assertThrows(InvalidCryptoParametersException.class, () -> SymmetricCipher.encrypt(cbc, longKey, data));
            assertThrows(InvalidCryptoParametersException.class, () -> SymmetricCipher.encrypt(cbc, shortIv, data));
            assertThrows(InvalidCryptoParametersException.class, () -> SymmetricCipher.decrypt(cbc, desIvForAes, data));
            assertThrows(InvalidCryptoParametersException.class, () -> SymmetricCipher.encrypt(ecb, ecbWithIv, data));
            assertThrows(InvalidCryptoParametersException.class, () -> SymmetricCipher.encrypt(cbc, cbcWithoutIv, data));
            assertThrows(InvalidCryptoParametersException.class, () -> SymmetricCipher.encrypt(des, aesKeyForDes, data));
        }
    }

    @Test
    void nullArgumentsAreRejected() throws Exception {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        try (DerivedKey key = CryptoTestData.fixedKey(spec)) {
            assertThrows(InvalidCryptoParametersException.class, () -> SymmetricCipher.encrypt(null, key, new byte[1]));
            assertThrows(InvalidCryptoParametersException.class, () -> SymmetricCipher.encrypt(spec, null, new byte[1]));
            assertThrows(InvalidCryptoParametersException.class, () -> SymmetricCipher.encrypt(spec, key, null));
            assertThrows(InvalidCryptoParametersException.class, () -> SymmetricCipher.decrypt(spec, key, null));
        }
    }

    @Test
    void cfb8DiffersFromCfb128AndFromOfb() throws Exception {
        byte[] key = hex(K128);
        byte[] iv = hex(IV);
        byte[] plaintext = CryptoTestData.pattern(64);
        CipherSpec cfb8 = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CFB8);
        CipherSpec ofb = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.OFB);
        try (DerivedKey derived = DerivedKey.of(key, iv)) {
            byte[] ours = SymmetricCipher.encrypt(cfb8, derived, plaintext);
            byte[] cfb128 = jce("AES/CFB/NoPadding", key, iv, plaintext);
            byte[] explicitCfb8 = jce("AES/CFB8/NoPadding", key, iv, plaintext);
            assertArrayEquals(explicitCfb8, ours);
            assertFalse(Arrays.equals(cfb128, ours), "CFB8 no puede coincidir con CFB de 128 bits");
            assertFalse(Arrays.equals(SymmetricCipher.encrypt(ofb, derived, plaintext), ours), "OFB no puede coincidir con CFB8");
            // en CFB de 128 bits el primer bloque sí coincide con CFB8 solo en el primer byte
            assertEquals(ours[0], cfb128[0]);
            assertNotEquals(ours[1], cfb128[1]);
        }
    }

    @Test
    void desedeCfb8AndOfbAlsoDifferFromBlockFeedbackVariants() throws Exception {
        byte[] key = CryptoTestData.seeded(5, 24);
        byte[] iv = CryptoTestData.seeded(6, 8);
        byte[] plaintext = CryptoTestData.pattern(40);
        try (DerivedKey derived = DerivedKey.of(key, iv)) {
            byte[] cfb8 = SymmetricCipher.encrypt(new CipherSpec(CryptoAlgorithm.TRIPLE_DES, CryptoMode.CFB8), derived, plaintext);
            assertFalse(Arrays.equals(cfb8, jce("DESede/CFB/NoPadding", key, iv, plaintext)));
            assertArrayEquals(jce("DESede/CFB8/NoPadding", key, iv, plaintext), cfb8);
            byte[] ofb = SymmetricCipher.encrypt(new CipherSpec(CryptoAlgorithm.TRIPLE_DES, CryptoMode.OFB), derived, plaintext);
            assertArrayEquals(jce("DESede/OFB/NoPadding", key, iv, plaintext), ofb);
        }
    }

    @Test
    void flippingACiphertextBitInCfb8CorruptsFollowingBytesButOfbOnlyOneBit() throws Exception {
        for (CryptoAlgorithm algorithm : new CryptoAlgorithm[] {CryptoAlgorithm.AES_128, CryptoAlgorithm.TRIPLE_DES}) {
            int block = algorithm.blockBytes();
            int position = 20;
            byte[] plaintext = new byte[64];

            CipherSpec cfb8 = new CipherSpec(algorithm, CryptoMode.CFB8);
            try (DerivedKey key = CryptoTestData.fixedKey(cfb8)) {
                byte[] ciphertext = SymmetricCipher.encrypt(cfb8, key, plaintext);
                ciphertext[position] ^= 0x10;
                byte[] decrypted = SymmetricCipher.decrypt(cfb8, key, ciphertext);
                for (int i = 0; i < position; i++) {
                    assertEquals(0, decrypted[i], algorithm + " antes del byte alterado, i=" + i);
                }
                assertEquals(0x10, decrypted[position], algorithm + " el bit alterado se refleja en el plaintext");
                for (int i = position + 1; i <= position + block; i++) {
                    assertNotEquals(0, decrypted[i], algorithm + " byte corrompido por la realimentación, i=" + i);
                }
                for (int i = position + block + 1; i < decrypted.length; i++) {
                    assertEquals(0, decrypted[i], algorithm + " tras resincronizar, i=" + i);
                }
            }

            CipherSpec ofb = new CipherSpec(algorithm, CryptoMode.OFB);
            try (DerivedKey key = CryptoTestData.fixedKey(ofb)) {
                byte[] ciphertext = SymmetricCipher.encrypt(ofb, key, plaintext);
                ciphertext[position] ^= 0x10;
                byte[] decrypted = SymmetricCipher.decrypt(ofb, key, ciphertext);
                for (int i = 0; i < decrypted.length; i++) {
                    assertEquals(i == position ? 0x10 : 0, decrypted[i], algorithm + " OFB, i=" + i);
                }
            }
        }
    }

    @Test
    void errorMessagesDoNotContainKeyOrIv() throws Exception {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        byte[] keyBytes = hex("E5FB03E4ABD3D239CBB4F0AAA3572401");
        byte[] ivBytes = hex("20EFCD7A8C7D9986B0BF3089EFF2D9B7");
        try (DerivedKey key = DerivedKey.of(keyBytes, ivBytes)) {
            Exception badPadding = assertThrows(InvalidPaddingException.class,
                    () -> SymmetricCipher.decrypt(spec, key, CryptoTestData.seeded(3, 32)));
            Exception badLength = assertThrows(InvalidCiphertextException.class,
                    () -> SymmetricCipher.decrypt(spec, key, new byte[15]));
            for (Exception e : new Exception[] {badPadding, badLength}) {
                String text = (e.getMessage() + " " + e).toLowerCase();
                assertFalse(text.contains("e5fb03e4"));
                assertFalse(text.contains("20efcd7a"));
            }
        }
    }

    @Test
    void sameInstanceOfSpecAndKeyCanBeUsedFromManyThreads() throws Exception {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_256, CryptoMode.CBC);
        try (DerivedKey key = CryptoTestData.fixedKey(spec)) {
            var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
            try {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
                for (int t = 0; t < 8; t++) {
                    final int seed = t;
                    futures.add(pool.submit(() -> {
                        for (int i = 0; i < 50; i++) {
                            byte[] data = CryptoTestData.seeded(seed * 1000L + i, i * 7);
                            if (!Arrays.equals(data, SymmetricCipher.decrypt(spec, key, SymmetricCipher.encrypt(spec, key, data)))) {
                                return false;
                            }
                        }
                        return true;
                    }));
                }
                for (var future : futures) {
                    assertTrue(future.get());
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private static byte[] jce(String transformation, byte[] key, byte[] iv, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(transformation);
        String algorithm = transformation.substring(0, transformation.indexOf('/'));
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, algorithm), new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }
}
