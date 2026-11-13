package ar.edu.itba.stegobmp.steganography;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Lsb4Test {
    private final Lsb4 lsb4 = new Lsb4();

    @Test
    void embedsMessageHighNibbleThenLowNibbleIntoLowCarrierNibbles() throws Exception {
        byte[] body = {(byte) 0xA0, 0x50, (byte) 0xF0, 0x00, 0x7F};
        byte[] expected = {(byte) 0xAA, 0x5B, (byte) 0xFC, 0x05, 0x7F};
        assertArrayEquals(expected, lsb4.embed(body, new byte[] {(byte) 0xAB, (byte) 0xC5}));
    }

    @Test
    void extractionVectorReadsTwoLowNibblesPerByte() throws Exception {
        byte[] body = {(byte) 0x1A, (byte) 0xE2, 0x3B, (byte) 0x80};
        assertArrayEquals(new byte[] {(byte) 0xA2, (byte) 0xB0}, lsb4.newReader(body).readBytes(2));
    }

    @Test
    void lsbFirstOrderTransmitsLowNibbleFirst() throws Exception {
        Lsb4 lsbFirst = new Lsb4(BitOrder.LSB_FIRST);
        byte[] body = {(byte) 0xA0, 0x50, (byte) 0xF0, 0x00};
        byte[] expected = {(byte) 0xAB, 0x5A, (byte) 0xF5, 0x0C};
        assertArrayEquals(expected, lsbFirst.embed(body, new byte[] {(byte) 0xAB, (byte) 0xC5}));
        assertArrayEquals(new byte[] {(byte) 0xAB, (byte) 0xC5},
                lsbFirst.newReader(expected).readBytes(2));
    }

    @Test
    void roundTripsEveryByteAndSeededData() throws Exception {
        byte[] allValues = new byte[256];
        for (int i = 0; i < allValues.length; i++) {
            allValues[i] = (byte) i;
        }
        byte[] body = new byte[allValues.length * 2 + 1];
        new Random(0x4C534234L).nextBytes(body);
        byte[] embedded = lsb4.embed(body, allValues);
        assertArrayEquals(allValues, lsb4.newReader(embedded).readBytes(allValues.length));

        byte[] randomData = new byte[512];
        new Random(0x53454544L).nextBytes(randomData);
        byte[] randomBody = new byte[randomData.length * 2];
        new Random(0x434F564552L).nextBytes(randomBody);
        assertArrayEquals(randomData, lsb4.newReader(lsb4.embed(randomBody, randomData)).readBytes(randomData.length));
    }

    @Test
    void emptyMessageLeavesBodyUnchanged() throws Exception {
        byte[] body = new byte[17];
        new Random(17).nextBytes(body);
        assertArrayEquals(body, lsb4.embed(body, new byte[0]));
        assertEquals(8, lsb4.newReader(body).remainingBytes());
        assertArrayEquals(new byte[0], lsb4.newReader(body).readBytes(0));
    }

    @Test
    void modifiesOnlyLowNibblesUsedByTheMessage() throws Exception {
        byte[] body = new byte[12];
        new Random(1234).nextBytes(body);
        byte[] original = body.clone();
        byte[] data = {(byte) 0x91, 0x27};
        byte[] result = lsb4.embed(body, data);

        assertArrayEquals(original, body);
        for (int i = 0; i < result.length; i++) {
            if (i < data.length * 2) {
                assertEquals(0, (result[i] ^ original[i]) & 0xF0, "upper nibble changed at " + i);
            } else {
                assertEquals(original[i], result[i], "unused carrier byte changed at " + i);
            }
        }
    }

    @Test
    void exactCapacityFitsAndOneMoreByteIsRejected() throws Exception {
        byte[] body = new byte[21];
        Arrays.fill(body, (byte) 0xA5);
        byte[] data = new byte[10];
        new Random(10).nextBytes(data);
        assertEquals(10, lsb4.capacityBytes(body.length));
        assertArrayEquals(data, lsb4.newReader(lsb4.embed(body, data)).readBytes(data.length));

        CapacityExceededException exception = assertThrows(
                CapacityExceededException.class, () -> lsb4.embed(body, new byte[11]));
        assertEquals(11, exception.requiredBytes());
        assertEquals(10, exception.capacityBytes());
    }

    @Test
    void readerRejectsOverreadWithoutConsumingRemainingData() throws Exception {
        StegoReader reader = lsb4.newReader(new byte[8]);
        assertThrows(StegoDataException.class, () -> reader.readBytes(5));
        assertEquals(4, reader.remainingBytes());
        assertEquals(0, reader.readByte());
    }

    @Test
    void negativeBodyLengthIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> lsb4.capacityBytes(-1));
    }
}
