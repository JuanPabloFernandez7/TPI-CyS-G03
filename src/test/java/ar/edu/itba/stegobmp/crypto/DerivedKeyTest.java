package ar.edu.itba.stegobmp.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class DerivedKeyTest {

    @Test
    void accessorsReturnIndependentCopies() throws Exception {
        byte[] key = {1, 2, 3, 4};
        byte[] iv = {5, 6};
        try (DerivedKey derived = DerivedKey.of(key, iv)) {
            key[0] = 99;
            iv[0] = 99;
            byte[] k1 = derived.keyBytes();
            byte[] i1 = derived.ivBytes();
            assertArrayEquals(new byte[] {1, 2, 3, 4}, k1);
            assertArrayEquals(new byte[] {5, 6}, i1);
            k1[0] = 42;
            assertArrayEquals(new byte[] {1, 2, 3, 4}, derived.keyBytes());
            assertNotSame(derived.keyBytes(), derived.keyBytes());
            assertEquals(4, derived.keyLength());
            assertEquals(2, derived.ivLength());
        }
    }

    @Test
    void closeZeroesTheInternalArrays() throws Exception {
        DerivedKey derived = DerivedKey.of(CryptoTestData.seeded(1, 16), CryptoTestData.seeded(2, 16));
        byte[] internalKey = internal(derived, "key");
        byte[] internalIv = internal(derived, "iv");
        assertFalse(isZero(internalKey));
        assertFalse(isZero(internalIv));
        derived.close();
        assertArrayEquals(new byte[16], internalKey);
        assertArrayEquals(new byte[16], internalIv);
    }

    @Test
    void usingAClosedKeyFails() throws Exception {
        DerivedKey derived = DerivedKey.of(new byte[16]);
        derived.close();
        assertThrows(IllegalStateException.class, derived::keyBytes);
        assertThrows(IllegalStateException.class, derived::ivBytes);
        derived.close();
    }

    @Test
    void ecbKeyHasEmptyIv() throws Exception {
        try (DerivedKey derived = DerivedKey.of(new byte[16])) {
            assertEquals(0, derived.ivLength());
        }
    }

    @Test
    void nullOrEmptyKeyIsRejected() {
        assertThrows(InvalidCryptoParametersException.class, () -> DerivedKey.of(null, new byte[0]));
        assertThrows(InvalidCryptoParametersException.class, () -> DerivedKey.of(new byte[16], null));
        assertThrows(InvalidCryptoParametersException.class, () -> DerivedKey.of(new byte[0], new byte[0]));
    }

    @Test
    void toStringDoesNotRevealTheKey() throws Exception {
        byte[] key = CryptoTestData.hex("E5FB03E4ABD3D239CBB4F0AAA3572401");
        try (DerivedKey derived = DerivedKey.of(key, CryptoTestData.hex("20EFCD7A8C7D9986B0BF3089EFF2D9B7"))) {
            String text = derived.toString().toLowerCase();
            assertFalse(text.contains("e5fb03e4"));
            assertFalse(text.contains("20efcd7a"));
        }
    }

    private static byte[] internal(DerivedKey derived, String field) throws Exception {
        Field f = DerivedKey.class.getDeclaredField(field);
        f.setAccessible(true);
        return (byte[]) f.get(derived);
    }

    private static boolean isZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
