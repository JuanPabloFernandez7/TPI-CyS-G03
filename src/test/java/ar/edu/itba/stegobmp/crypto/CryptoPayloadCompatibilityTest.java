package ar.edu.itba.stegobmp.crypto;

import static ar.edu.itba.stegobmp.crypto.CryptoTestData.concat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifica que la capa criptográfica sirve al formato de la consigna sin depender del paquete payload:
 * la estructura se arma a mano como {@code u32be(tamaño) || datos || ".txt\0"}.
 */
class CryptoPayloadCompatibilityTest {

    static Stream<CipherSpec> specs() {
        return CryptoTestData.allSpecs().stream();
    }

    @ParameterizedTest
    @MethodSource("specs")
    void consignaExampleOf414BytesRoundTripsThroughTheFramedStream(CipherSpec spec) throws Exception {
        byte[] data = CryptoTestData.seeded(414, 414);
        byte[] plaintext = concat(ByteBuffer.allocate(4).putInt(414).array(), data, ".txt\0".getBytes(StandardCharsets.US_ASCII));
        assertEquals(423, plaintext.length);

        try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(spec, "oculto".toCharArray())) {
            byte[] ciphertext = cipher.encrypt(plaintext);
            int expected = !spec.isPadded() ? 423 : spec.algorithm() == CryptoAlgorithm.TRIPLE_DES ? 424 : 432;
            assertEquals(expected, ciphertext.length);

            byte[] stream = concat(ByteBuffer.allocate(4).putInt(ciphertext.length).array(), ciphertext);
            assertEquals(expected + 4, stream.length);
            if (spec.isPadded()) {
                byte[] header = Arrays.copyOf(stream, 4);
                assertArrayEquals(spec.algorithm() == CryptoAlgorithm.TRIPLE_DES ? new byte[] {0, 0, 0x01, (byte) 0xA8}
                        : new byte[] {0, 0, 0x01, (byte) 0xB0}, header);
            }

            int declared = ByteBuffer.wrap(stream, 0, 4).getInt();
            byte[] block = Arrays.copyOfRange(stream, 4, 4 + declared);
            byte[] decrypted = cipher.decrypt(block);
            assertArrayEquals(plaintext, decrypted);
            assertEquals(414, ByteBuffer.wrap(decrypted, 0, 4).getInt());
        }
    }
}
