package ar.edu.itba.stegobmp.steganography;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    void embedsInBlueGreenAndPaddingAndPreservesRed() throws Exception {
        CarrierLayout layout = new CarrierLayout(1, 10, 4);
        byte[] body = new byte[(int) layout.bodyLength()];
        Arrays.fill(body, (byte) 0xA6);
        byte[] result = lsbi.embed(body, new byte[] {(byte) 0xA5}, layout);
        int[] usedPositions = firstDataPositions(body, layout, Byte.SIZE);

        for (int index = 4; index < body.length; index++) {
            if (!layout.isLsbiDataByte(index)
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
    void paddingInTheReservedPrefixStoresTheFourthFlag() throws Exception {
        CarrierLayout layout = new CarrierLayout(1, 4, 4);
        byte[] body = new byte[16];
        Arrays.fill(body, (byte) 0x06);

        byte[] result = lsbi.embed(body, new byte[] {(byte) 0xFF}, layout);

        assertArrayEquals(new byte[] {0x06, 0x06, 0x06, 0x07}, Arrays.copyOf(result, 4));
        assertEquals(0xFF, lsbi.newReader(result, layout).readByte());
        assertEquals(0x06, body[3], "The input prefix remains unchanged");
    }

    static Stream<Arguments> paddedVectors() {
        return Stream.of(
                Arguments.of(1, 4L, new int[] {4, 5, 7, 8, 9, 11, 12, 13}, 1),
                Arguments.of(2, 8L, new int[] {4, 6, 7, 8, 9, 11, 12, 14}, 2),
                Arguments.of(3, 12L, new int[] {4, 6, 7, 9, 10, 11, 12, 13}, 4));
    }

    @ParameterizedTest(name = "width {0}, stride {1}")
    @MethodSource("paddedVectors")
    void manualVectorsIncludePaddingAcrossRowsAndEnforceCapacity(
            int width, long stride, int[] positions, int expectedCapacity) throws Exception {
        CarrierLayout layout = new CarrierLayout(width, 4, stride);
        byte[] body = new byte[(int) layout.bodyLength()];
        byte[] expected = body.clone();
        // A5 is 10100101, giving four changes and four unchanged bits for pattern 00.
        for (int bit : new int[] {0, 2, 5, 7}) {
            expected[positions[bit]] = 1;
        }

        assertArrayEquals(expected, lsbi.embed(body, new byte[] {(byte) 0xA5}, layout));
        assertEquals(0xA5, lsbi.newReader(expected, layout).readByte());
        assertEquals(expectedCapacity, lsbi.capacityBytes(body.length, layout));
        assertEquals(expectedCapacity,
                lsbi.newReader(lsbi.embed(body, new byte[expectedCapacity], layout), layout).remainingBytes());
        assertThrows(CapacityExceededException.class,
                () -> lsbi.embed(body, new byte[expectedCapacity + 1], layout));
    }

    @Test
    void exactCapacityFitsAndOneAdditionalByteIsRejected() throws Exception {
        CarrierLayout exact = new CarrierLayout(1, 28, 4);
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

        assertThrows(StegoDataException.class, () -> reader.readBytes(4));
        assertEquals(3, reader.remainingBytes());
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
            while (next < body.length && !layout.isLsbiDataByte(next)) {
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
