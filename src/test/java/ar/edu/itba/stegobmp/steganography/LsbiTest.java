package ar.edu.itba.stegobmp.steganography;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LsbiTest {
    private static final CarrierLayout LAYOUT = new CarrierLayout(2, 5, 8);

    private final Lsbi lsbi = new Lsbi();

    @Test
    void identifiesAllFourPatternsFromTheSecondAndThirdLeastSignificantBits() {
        assertEquals(0b00, Lsbi.patternOf(0b0000_0000));
        assertEquals(0b01, Lsbi.patternOf(0b0000_0010));
        assertEquals(0b10, Lsbi.patternOf(0b0000_0100));
        assertEquals(0b11, Lsbi.patternOf(0b0000_0110));
        assertEquals(0b10, Lsbi.patternOf(0b1000_1100));
    }

    @Test
    void invertsOnlyWhenChangesAreStrictlyMoreFrequentThanUnchangedBits() {
        assertTrue(Lsbi.shouldInvert(5, 4));
        assertFalse(Lsbi.shouldInvert(4, 4));
        assertFalse(Lsbi.shouldInvert(3, 5));
        assertFalse(Lsbi.shouldInvert(0, 0));
    }

    @Test
    void manualVectorInvertsPattern10AndStoresItsFlagInTheThirdPrefixByte() throws Exception {
        byte[] body = new byte[(int) LAYOUT.bodyLength()];
        Arrays.fill(body, (byte) 0b0000_0100);

        byte[] result = lsbi.embed(body, new byte[] {(byte) 0xFF}, LAYOUT);

        assertArrayEquals(new byte[] {0x04, 0x04, 0x05, 0x04}, Arrays.copyOf(result, 4));
        assertEquals(0xFF, lsbi.newReader(result, LAYOUT).readByte());
        assertEquals(0, body[2] & 1, "The input body must not be modified");
    }

    @Test
    void manualVectorStoresAndRecoversAllFourInversionFlags() throws Exception {
        byte[] body = new byte[(int) LAYOUT.bodyLength()];
        int[] positions = firstDataPositions(body, LAYOUT, 8);
        for (int index = 0; index < positions.length; index++) {
            body[positions[index]] = (byte) ((index % 4) << 1);
        }

        byte[] result = lsbi.embed(body, new byte[] {(byte) 0xFF}, LAYOUT);

        assertEquals(0b1111, flags(result));
        assertEquals(0xFF, lsbi.newReader(result, LAYOUT).readByte());
    }

    @Test
    void tiesDoNotInvertAndUseOrdinaryLsbReplacement() throws Exception {
        byte[] body = new byte[(int) LAYOUT.bodyLength()];
        int[] positions = firstDataPositions(body, LAYOUT, 8);
        for (int index = 0; index < positions.length / 2; index++) {
            body[positions[index]] = 1;
        }

        byte[] result = lsbi.embed(body, new byte[] {0}, LAYOUT);

        assertEquals(0, flags(result));
        assertArrayEquals(new byte[] {0}, lsbi.newReader(result, LAYOUT).readBytes(1));
        for (int position : positions) {
            assertEquals(0, result[position] & 1);
        }
    }

    @Test
    void embedsOnlyInBlueAndGreenAndPreservesRedAndPadding() throws Exception {
        CarrierLayout layout = new CarrierLayout(1, 10, 4);
        byte[] body = new byte[(int) layout.bodyLength()];
        Arrays.fill(body, (byte) 0xA6);
        byte[] result = lsbi.embed(body, new byte[] {(byte) 0xA5}, layout);
        int[] usedPositions = firstDataPositions(body, layout, Byte.SIZE);

        for (int index = 4; index < body.length; index++) {
            if (!layout.isBlueOrGreenComponent(index)
                    || Arrays.binarySearch(usedPositions, index) < 0) {
                assertEquals(body[index], result[index], "Unexpected change at body index " + index);
            } else {
                assertEquals(0, (body[index] ^ result[index]) & 0xFE,
                        "A non-LSB bit changed at body index " + index);
            }
        }
        assertArrayEquals(new byte[] {(byte) 0xA5}, lsbi.newReader(result, layout).readBytes(1));
    }

    @Test
    void paddingCanChangeOnlyWhenItIsOneOfTheFourFlagBytes() throws Exception {
        CarrierLayout layout = new CarrierLayout(1, 1, 4);
        byte[] body = {(byte) 0xA7, (byte) 0xA7, (byte) 0xA7, (byte) 0xA7};

        byte[] result = lsbi.embed(body, new byte[0], layout);

        for (int index = 0; index < body.length; index++) {
            assertEquals(0, (body[index] ^ result[index]) & 0xFE);
            assertEquals(0, result[index] & 1, "The inversion flags are all zero");
        }
        assertEquals(0, result[3] & 1, "The fourth physical byte is row padding but stores a flag");
    }

    @Test
    void exactCapacityFitsAndOneAdditionalByteIsRejected() throws Exception {
        CarrierLayout exact = new CarrierLayout(1, 41, 4);
        byte[] body = new byte[(int) exact.bodyLength()];
        assertEquals(10, lsbi.capacityBytes(body.length, exact));
        assertEquals(10, lsbi.newReader(body, exact).remainingBytes());
        assertEquals(10, lsbi.newReader(lsbi.embed(body, new byte[10], exact), exact).remainingBytes());

        CapacityExceededException error = assertThrows(
                CapacityExceededException.class, () -> lsbi.embed(body, new byte[11], exact));
        assertEquals(11, error.requiredBytes());
        assertEquals(10, error.capacityBytes());
    }

    @Test
    void fewerThanFourPrefixBytesHaveNoPayloadCapacity() throws Exception {
        CarrierLayout tiny = new CarrierLayout(1, 1, 3);
        byte[] body = {1, 2, 3};

        assertEquals(0, lsbi.capacityBytes(body.length, tiny));
        assertArrayEquals(new byte[] {0, 2, 2}, lsbi.embed(body, new byte[0], tiny));
        assertThrows(CapacityExceededException.class, () -> lsbi.embed(body, new byte[1], tiny));
    }

    @Test
    void readerRejectsOverreadWithoutConsumingRemainingData() throws Exception {
        byte[] body = new byte[(int) LAYOUT.bodyLength()];
        StegoReader reader = lsbi.newReader(body, LAYOUT);

        assertThrows(StegoDataException.class, () -> reader.readBytes(3));
        assertEquals(2, reader.remainingBytes());
        assertEquals(0, reader.readByte());
    }

    @Test
    void geometryMustMatchTheBody() {
        assertThrows(IllegalArgumentException.class,
                () -> lsbi.capacityBytes((int) LAYOUT.bodyLength() - 1, LAYOUT));
        assertThrows(IllegalArgumentException.class,
                () -> new CarrierLayout(1, 1, 2));
    }

    @Test
    void geometryFreeContractMethodsAreRejected() {
        assertThrows(UnsupportedOperationException.class, () -> lsbi.capacityBytes(40));
        assertThrows(UnsupportedOperationException.class, () -> lsbi.embed(new byte[40], new byte[0]));
        assertThrows(UnsupportedOperationException.class, () -> lsbi.newReader(new byte[40]));
    }

    private static int[] firstDataPositions(byte[] body, CarrierLayout layout, int count) {
        int[] positions = new int[count];
        int next = 4;
        for (int index = 0; index < count; index++) {
            while (next < body.length && !layout.isBlueOrGreenComponent(next)) {
                next++;
            }
            positions[index] = next++;
        }
        return positions;
    }

    private static int flags(byte[] body) {
        int result = 0;
        for (int index = 0; index < 4; index++) {
            result |= (body[index] & 1) << index;
        }
        return result;
    }
}
