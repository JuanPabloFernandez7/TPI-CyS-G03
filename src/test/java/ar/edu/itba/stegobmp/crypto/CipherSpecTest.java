package ar.edu.itba.stegobmp.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CipherSpecTest {

    static Stream<Arguments> expectedParameters() {
        // algoritmo, modo, transformación, bytes de IV, bytes derivados
        return Stream.of(
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.ECB, "AES/ECB/PKCS5Padding", 0, 16),
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.CBC, "AES/CBC/PKCS5Padding", 16, 32),
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.CFB8, "AES/CFB8/NoPadding", 16, 32),
                Arguments.of(CryptoAlgorithm.AES_128, CryptoMode.OFB, "AES/OFB/NoPadding", 16, 32),
                Arguments.of(CryptoAlgorithm.AES_192, CryptoMode.ECB, "AES/ECB/PKCS5Padding", 0, 24),
                Arguments.of(CryptoAlgorithm.AES_192, CryptoMode.CBC, "AES/CBC/PKCS5Padding", 16, 40),
                Arguments.of(CryptoAlgorithm.AES_192, CryptoMode.CFB8, "AES/CFB8/NoPadding", 16, 40),
                Arguments.of(CryptoAlgorithm.AES_192, CryptoMode.OFB, "AES/OFB/NoPadding", 16, 40),
                Arguments.of(CryptoAlgorithm.AES_256, CryptoMode.ECB, "AES/ECB/PKCS5Padding", 0, 32),
                Arguments.of(CryptoAlgorithm.AES_256, CryptoMode.CBC, "AES/CBC/PKCS5Padding", 16, 48),
                Arguments.of(CryptoAlgorithm.AES_256, CryptoMode.CFB8, "AES/CFB8/NoPadding", 16, 48),
                Arguments.of(CryptoAlgorithm.AES_256, CryptoMode.OFB, "AES/OFB/NoPadding", 16, 48),
                Arguments.of(CryptoAlgorithm.TRIPLE_DES, CryptoMode.ECB, "DESede/ECB/PKCS5Padding", 0, 24),
                Arguments.of(CryptoAlgorithm.TRIPLE_DES, CryptoMode.CBC, "DESede/CBC/PKCS5Padding", 8, 32),
                Arguments.of(CryptoAlgorithm.TRIPLE_DES, CryptoMode.CFB8, "DESede/CFB8/NoPadding", 8, 32),
                Arguments.of(CryptoAlgorithm.TRIPLE_DES, CryptoMode.OFB, "DESede/OFB/NoPadding", 8, 32));
    }

    @ParameterizedTest
    @MethodSource("expectedParameters")
    void sixteenCombinationsHaveTheDocumentedParameters(CryptoAlgorithm algorithm, CryptoMode mode,
            String transformation, int ivBytes, int derivedBytes) {
        CipherSpec spec = new CipherSpec(algorithm, mode);
        assertEquals(transformation, spec.transformation());
        assertEquals(ivBytes, spec.ivBytes());
        assertEquals(derivedBytes, spec.derivedBytes());
    }

    @Test
    void transformationNeverUsesBareCfbOrOfb8() {
        for (CipherSpec spec : CryptoTestData.allSpecs()) {
            String t = spec.transformation();
            assertFalse(t.contains("/CFB/"), t);
            assertFalse(t.contains("/OFB8/"), t);
            assertEquals(3, t.split("/").length, t);
        }
    }

    @Test
    void onlyEcbHasNoIvAndOnlyEcbAndCbcArePadded() {
        for (CryptoMode mode : CryptoMode.values()) {
            assertEquals(mode != CryptoMode.ECB, mode.usesIv(), mode.name());
            assertEquals(mode == CryptoMode.ECB || mode == CryptoMode.CBC, mode.isPadded(), mode.name());
        }
    }

    @Test
    void keyAndBlockSizesMatchTheAssignment() {
        assertEquals(16, CryptoAlgorithm.AES_128.keyBytes());
        assertEquals(24, CryptoAlgorithm.AES_192.keyBytes());
        assertEquals(32, CryptoAlgorithm.AES_256.keyBytes());
        assertEquals(24, CryptoAlgorithm.TRIPLE_DES.keyBytes());
        assertEquals(16, CryptoAlgorithm.AES_256.blockBytes());
        assertEquals(8, CryptoAlgorithm.TRIPLE_DES.blockBytes());
    }

    @Test
    void ciphertextLengthTable() {
        CipherSpec aesCbc = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        CipherSpec desEcb = new CipherSpec(CryptoAlgorithm.TRIPLE_DES, CryptoMode.ECB);
        long[] plaintext = {0, 1, 7, 8, 15, 16, 17, 423, 432};
        long[] aes = {16, 16, 16, 16, 16, 32, 32, 432, 448};
        long[] des = {8, 8, 8, 16, 16, 24, 24, 424, 440};
        for (int i = 0; i < plaintext.length; i++) {
            assertEquals(aes[i], aesCbc.ciphertextLength(plaintext[i]), "AES P=" + plaintext[i]);
            assertEquals(des[i], desEcb.ciphertextLength(plaintext[i]), "3DES P=" + plaintext[i]);
        }
    }

    @Test
    void unpaddedModesKeepTheLength() {
        for (CryptoAlgorithm algorithm : CryptoAlgorithm.values()) {
            for (CryptoMode mode : new CryptoMode[] {CryptoMode.CFB8, CryptoMode.OFB}) {
                CipherSpec spec = new CipherSpec(algorithm, mode);
                for (long p : new long[] {0, 1, 15, 16, 423, Long.MAX_VALUE}) {
                    assertEquals(p, spec.ciphertextLength(p));
                }
            }
        }
    }

    @Test
    void ciphertextLengthNearLongMaxFailsExplicitly() {
        CipherSpec aes = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        CipherSpec des = new CipherSpec(CryptoAlgorithm.TRIPLE_DES, CryptoMode.ECB);
        assertThrows(ArithmeticException.class, () -> aes.ciphertextLength(Long.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> des.ciphertextLength(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE - 15, aes.ciphertextLength(Long.MAX_VALUE - 16));
        assertThrows(ArithmeticException.class, () -> aes.ciphertextLength(Long.MAX_VALUE - 15));
    }

    @Test
    void negativeLengthIsRejected() {
        CipherSpec spec = new CipherSpec(CryptoAlgorithm.AES_128, CryptoMode.CBC);
        assertThrows(IllegalArgumentException.class, () -> spec.ciphertextLength(-1));
    }

    @Test
    void nullComponentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new CipherSpec(null, CryptoMode.CBC));
        assertThrows(NullPointerException.class, () -> new CipherSpec(CryptoAlgorithm.AES_128, null));
    }
}
