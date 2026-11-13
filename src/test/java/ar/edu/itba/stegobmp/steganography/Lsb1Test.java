package ar.edu.itba.stegobmp.steganography;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Lsb1Test {
    private final Lsb1 lsb1 = new Lsb1();

    /** Convierte "00110011 11101001" en bytes; los vectores esperados se escriben a mano. */
    private static byte[] bytes(String binary) {
        String[] groups = binary.trim().split("\\s+");
        byte[] out = new byte[groups.length];
        for (int i = 0; i < groups.length; i++) {
            out[i] = (byte) Integer.parseInt(groups[i], 2);
        }
        return out;
    }

    private static byte[] filled(int length, int value) {
        byte[] b = new byte[length];
        Arrays.fill(b, (byte) value);
        return b;
    }

    private byte[] roundTrip(byte[] body, byte[] data) throws Exception {
        return lsb1.newReader(lsb1.embed(body, data)).readBytes(data.length);
    }

    // Caso 1: ejemplo LSB estándar del paper (§4), mensaje 1 0 0 0 1 1 1 0 = 0x8E, MSB primero.
    @Test
    void paperExampleVector() throws Exception {
        byte[] cover = bytes("00110011 11101001 01101010 10101001 11011000 10001101 10001100 01101101");
        byte[] expected = bytes("00110011 11101000 01101010 10101000 11011001 10001101 10001101 01101100");
        assertArrayEquals(expected, lsb1.embed(cover, new byte[] {(byte) 0x8E}));
    }

    // Caso 2: patrones no simétricos; un orden invertido daría otro resultado.
    @Test
    void nonSymmetricVectorsFixOrderIndependentlyOfRoundTrip() throws Exception {
        assertArrayEquals(bytes("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000001"),
                lsb1.embed(filled(8, 0x00), new byte[] {0x01}));
        assertArrayEquals(bytes("00000001 00000000 00000001 00000001 00000000 00000000 00000001 00000000"),
                lsb1.embed(filled(8, 0x00), new byte[] {(byte) 0xB2}));
        assertArrayEquals(bytes("11111111 11111110 11111111 11111111 11111110 11111110 11111111 11111110"),
                lsb1.embed(filled(8, 0xFF), new byte[] {(byte) 0xB2}));
        // 0x80 = 1000 0000: solo el primer byte portador lleva 1
        assertArrayEquals(bytes("00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000"),
                lsb1.embed(filled(8, 0x00), new byte[] {(byte) 0x80}));
    }

    // Caso 2 (extracción): LSBs 1,0,1,1,0,0,1,0 con bits altos arbitrarios = 0xB2.
    @Test
    void extractionVector() throws Exception {
        byte[] body = bytes("11111111 10101010 01010101 10000001 00000010 11111100 00000011 11111110");
        assertEquals(0xB2, lsb1.newReader(body).readByte());
    }

    // Caso 3
    @Test
    void zeroAndFfBytes() throws Exception {
        assertArrayEquals(filled(16, 0xFE), lsb1.embed(filled(16, 0xFF), new byte[] {0x00, 0x00}));
        assertArrayEquals(filled(16, 0x01), lsb1.embed(filled(16, 0x00), new byte[] {(byte) 0xFF, (byte) 0xFF}));
        assertArrayEquals(filled(8, 0xFF), lsb1.embed(filled(8, 0xFF), new byte[] {(byte) 0xFF}));
        assertArrayEquals(filled(8, 0x00), lsb1.embed(filled(8, 0x00), new byte[] {0x00}));
    }

    // Caso 4
    @Test
    void alternatingPatterns() throws Exception {
        assertArrayEquals(bytes("00000001 00000000 00000001 00000000 00000001 00000000 00000001 00000000"),
                lsb1.embed(filled(8, 0x00), new byte[] {(byte) 0xAA}));
        assertArrayEquals(bytes("11111110 11111111 11111110 11111111 11111110 11111111 11111110 11111111"),
                lsb1.embed(filled(8, 0xFF), new byte[] {0x55}));
    }

    // Caso 7: tamaño Big Endian de la consigna (17.118 = 00 00 42 DE) leído secuencialmente.
    @Test
    void bigEndianSizeStreamOfAssignmentExample() throws Exception {
        byte[] stream = {0x00, 0x00, 0x42, (byte) 0xDE};
        byte[] embedded = lsb1.embed(filled(32, 0x00), stream);
        // Bits esperados escritos a mano: 00000000 00000000 01000010 11011110
        String expectedLsbs = "00000000000000000100001011011110";
        StringBuilder actual = new StringBuilder();
        for (byte b : embedded) {
            actual.append(b & 1);
        }
        assertEquals(expectedLsbs, actual.toString());
    }

    // Caso 7: size || datos || ".txt\0" leído con el lector secuencial
    @Test
    void sequentialReadOfSizeDataExtension() throws Exception {
        byte[] data = "hola".getBytes();
        byte[] ext = {'.', 't', 'x', 't', 0};
        byte[] stream = new byte[4 + data.length + ext.length];
        stream[3] = (byte) data.length;
        System.arraycopy(data, 0, stream, 4, data.length);
        System.arraycopy(ext, 0, stream, 4 + data.length, ext.length);

        byte[] body = new byte[200];
        new Random(7).nextBytes(body);
        StegoReader reader = lsb1.newReader(lsb1.embed(body, stream));
        assertEquals(25, reader.remainingBytes());
        byte[] sizeField = reader.readBytes(4);
        assertArrayEquals(new byte[] {0, 0, 0, 4}, sizeField);
        assertArrayEquals(data, reader.readBytes(4));
        StringBuilder extension = new StringBuilder();
        int b;
        while ((b = reader.readByte()) != 0) {
            extension.append((char) b);
        }
        assertEquals(".txt", extension.toString());
        assertEquals(25 - 4 - 4 - 5, reader.remainingBytes());
    }

    // Caso 5
    @Test
    void roundTripSingleByteSeveralBytesAndAllValues() throws Exception {
        byte[] body = new byte[8 * 300];
        new Random(1).nextBytes(body);
        assertArrayEquals(new byte[] {0x5A}, roundTrip(body, new byte[] {0x5A}));
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, roundTrip(body, new byte[] {1, 2, 3, 4, 5}));

        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        assertArrayEquals(all, roundTrip(body, all));

        byte[] random = new byte[1000];
        new Random(42).nextBytes(random);
        byte[] bigBody = new byte[8000];
        new Random(43).nextBytes(bigBody);
        assertArrayEquals(random, roundTrip(bigBody, random));
    }

    // Caso 6
    @Test
    void emptyDataLeavesBodyUnchangedAndReaderHasNothing() throws Exception {
        byte[] body = new byte[16];
        new Random(3).nextBytes(body);
        byte[] result = lsb1.embed(body, new byte[0]);
        assertArrayEquals(body, result);
        assertEquals(0, lsb1.newReader(result).readBytes(0).length);
    }

    // Casos 8 y 9
    @Test
    void onlyAuthorizedBitsChange() throws Exception {
        for (int fill : new int[] {0x00, 0xFF, -1}) {
            byte[] body = new byte[8 * 64 + 5];
            if (fill >= 0) {
                Arrays.fill(body, (byte) fill);
            } else {
                new Random(9).nextBytes(body);
            }
            byte[] original = body.clone();
            byte[] data = new byte[40];
            new Random(11).nextBytes(data);

            byte[] result = lsb1.embed(body, data);

            assertArrayEquals(original, body, "el cuerpo de entrada no debe modificarse");
            assertEquals(body.length, result.length);
            for (int k = 0; k < body.length; k++) {
                assertEquals(0, (result[k] ^ original[k]) & 0xFE, "bits altos alterados en " + k);
                if (k >= 8 * data.length) {
                    assertEquals(original[k], result[k], "byte posterior al mensaje alterado en " + k);
                }
            }
        }
    }

    // Caso 15
    @Test
    void exactCapacityFitsAndOneMoreByteIsRejected() throws Exception {
        byte[] body = new byte[80];
        new Random(5).nextBytes(body);
        byte[] exact = new byte[10];
        new Random(6).nextBytes(exact);
        assertArrayEquals(exact, roundTrip(body, exact));

        CapacityExceededException e =
                assertThrows(CapacityExceededException.class, () -> lsb1.embed(body, new byte[11]));
        assertEquals(11, e.requiredBytes());
        assertEquals(10, e.capacityBytes());
        assertTrue(e.getMessage().contains("11") && e.getMessage().contains("10"), e.getMessage());
    }

    // Caso 16
    @Test
    void bodyLengthNotMultipleOfEightIgnoresTrailingBytes() throws Exception {
        assertEquals(10, lsb1.capacityBytes(87));
        byte[] body = filled(87, 0x00);
        byte[] result = lsb1.embed(body, filled(10, 0xFF));
        for (int k = 80; k < 87; k++) {
            assertEquals(0, result[k]);
        }
        assertThrows(CapacityExceededException.class, () -> lsb1.embed(body, new byte[11]));
        assertEquals(10, lsb1.newReader(result).remainingBytes());
    }

    // Caso 17
    @Test
    void emptyOrTinyBodyHasNoCapacity() {
        assertEquals(0, lsb1.capacityBytes(0));
        assertEquals(0, lsb1.capacityBytes(7));
        assertThrows(CapacityExceededException.class, () -> lsb1.embed(new byte[0], new byte[1]));
        assertThrows(CapacityExceededException.class, () -> lsb1.embed(new byte[7], new byte[1]));
        assertEquals(0, lsb1.newReader(new byte[7]).remainingBytes());
    }

    // Caso 18
    @Test
    void readingBeyondBodyFailsWithDetails() throws Exception {
        byte[] body = lsb1.embed(new byte[16], new byte[] {1, 2});
        StegoReader reader = lsb1.newReader(body);
        assertEquals(2, reader.remainingBytes());
        reader.readBytes(2);
        assertEquals(0, reader.remainingBytes());
        assertThrows(StegoDataException.class, reader::readByte);

        StegoReader other = lsb1.newReader(body);
        StegoDataException e = assertThrows(StegoDataException.class, () -> other.readBytes(3));
        assertTrue(e.getMessage().contains("3") && e.getMessage().contains("2"), e.getMessage());
        assertEquals(2, other.remainingBytes(), "una lectura fallida no consume bytes");
        assertThrows(StegoDataException.class, () -> other.readBytes(Integer.MAX_VALUE));
    }

    @Test
    void remainingBytesIsReadingCapacityNotMessageLength() throws Exception {
        byte[] body = lsb1.embed(new byte[800], new byte[] {9});
        StegoReader reader = lsb1.newReader(body);
        assertEquals(100, reader.remainingBytes());
        reader.readByte();
        assertEquals(99, reader.remainingBytes());
    }

    @Test
    void bitOrderIsTheOnlySwitchBetweenConventions() throws Exception {
        Lsb1 lsbFirst = new Lsb1(BitOrder.LSB_FIRST);
        assertArrayEquals(bytes("00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000000"),
                lsbFirst.embed(filled(8, 0), new byte[] {0x01}));
        assertNotEquals(Arrays.toString(lsb1.embed(filled(8, 0), new byte[] {0x01})),
                Arrays.toString(lsbFirst.embed(filled(8, 0), new byte[] {0x01})));
        byte[] embedded = lsbFirst.embed(filled(8, 0), new byte[] {(byte) 0xB2});
        assertEquals(0xB2, lsbFirst.newReader(embedded).readByte());
        assertNotEquals(0xB2, lsb1.newReader(embedded).readByte());
    }

    @Test
    void invalidArguments() {
        assertThrows(NullPointerException.class, () -> lsb1.embed(null, new byte[0]));
        assertThrows(NullPointerException.class, () -> lsb1.embed(new byte[8], null));
        assertThrows(IllegalArgumentException.class, () -> lsb1.capacityBytes(-1));
        assertThrows(IllegalArgumentException.class, () -> lsb1.newReader(new byte[8]).readBytes(-1));
    }
}
