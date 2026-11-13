package ar.edu.itba.stegobmp.bmp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BmpReaderTest {

    private static BmpImage parse(byte[] data) throws BmpFormatException {
        return BmpReader.parse(data);
    }

    private static String error(byte[] data) {
        return assertThrows(BmpFormatException.class, () -> BmpReader.parse(data)).getMessage();
    }

    @Test
    void validWithoutPadding() throws Exception {
        // ancho 4 -> 12 bytes por fila, múltiplo de 4
        byte[] data = BmpTestData.valid(4, 3).build();
        BmpImage img = parse(data);
        assertEquals(54, img.bodyOffset());
        assertEquals(36, img.bodyLength());
        assertEquals(12, img.header().stride());
        assertEquals(4, img.header().width());
        assertEquals(3, img.header().height());
        assertArrayEquals(Arrays.copyOfRange(data, 54, 90), img.copyBody());
        assertEquals(0, img.gapBytes().length);
        assertEquals(0, img.trailingBytes().length);
    }

    @Test
    void validWithPaddingIncludesPaddingInBody() throws Exception {
        // ancho 3 -> 9 bytes de píxeles + 3 de padding = stride 12
        byte[] data = BmpTestData.valid(3, 2).build();
        BmpImage img = parse(data);
        assertEquals(12, img.header().stride());
        assertEquals(24, img.bodyLength());
        assertArrayEquals(Arrays.copyOfRange(data, 54, 78), img.copyBody());
    }

    @ParameterizedTest
    @CsvSource({"1,4", "2,8", "3,12", "4,12", "5,16", "6,20", "7,24", "8,24"})
    void strideFormula(int width, int expectedStride) throws Exception {
        BmpImage img = parse(BmpTestData.valid(width, 1).build());
        assertEquals(expectedStride, img.header().stride());
        assertEquals(expectedStride, img.bodyLength());
    }

    @Test
    void offsetExactly54() throws Exception {
        BmpImage img = parse(BmpTestData.valid(2, 2).offset(54).build());
        assertEquals(54, img.bodyOffset());
        assertEquals(54, img.header().pixelOffset());
    }

    @Test
    void offsetGreaterThan54PreservesGapAndUsesRealOffset() throws Exception {
        byte[] data = BmpTestData.valid(2, 2).gap(10).build();
        BmpImage img = parse(data);
        assertEquals(64, img.bodyOffset());
        assertEquals(8 * 2, img.bodyLength());
        assertArrayEquals(Arrays.copyOfRange(data, 54, 64), img.gapBytes());
        assertArrayEquals(Arrays.copyOfRange(data, 64, 80), img.copyBody());
    }

    @Test
    void negativeHeightUsesAbsoluteValueAndPhysicalOrder() throws Exception {
        byte[] data = BmpTestData.valid(3, -2).build();
        BmpImage img = parse(data);
        assertEquals(-2, img.header().height());
        assertEquals(2, img.header().rowCount());
        assertEquals(24, img.bodyLength());
        assertArrayEquals(Arrays.copyOfRange(data, 54, 78), img.copyBody());
    }

    @Test
    void trailingBytesPreservedButNotPartOfBody() throws Exception {
        byte[] data = BmpTestData.valid(2, 2).trailing(7).build();
        BmpImage img = parse(data);
        assertEquals(16, img.bodyLength());
        assertArrayEquals(Arrays.copyOfRange(data, 70, 77), img.trailingBytes());
        assertEquals(data.length, img.fileLength());
    }

    @Test
    void sizeImageIsIgnored() throws Exception {
        assertEquals(16, parse(BmpTestData.valid(2, 2).sizeImage(0).build()).bodyLength());
        assertEquals(16, parse(BmpTestData.valid(2, 2).sizeImage(999_999).build()).bodyLength());
    }

    @Test
    void truncatedFileIsRejected() {
        byte[] data = BmpTestData.valid(2, 2).bodyLength(15).build();
        String message = error(data);
        assertTrue(message.contains("truncado"), message);
        assertTrue(error(BmpTestData.valid(2, 2).bodyLength(0).build()).contains("truncado"));
    }

    @Test
    void offsetBeyondFileIsTruncated() {
        assertTrue(error(BmpTestData.valid(2, 2).offset(70).build()).contains("truncado"));
    }

    @Test
    void incompleteHeaderIsRejected() {
        byte[] full = BmpTestData.valid(2, 2).build();
        assertTrue(error(new byte[0]).contains("Encabezado incompleto"));
        assertTrue(error(Arrays.copyOf(full, 53)).contains("Encabezado incompleto"));
        assertTrue(error(Arrays.copyOf(full, 2)).contains("Encabezado incompleto"));
    }

    @Test
    void headerOnlyWithNoBodyIsTruncated() {
        byte[] full = BmpTestData.valid(2, 2).build();
        assertTrue(error(Arrays.copyOf(full, 54)).contains("truncado"));
    }

    @Test
    void wrongMagic() {
        assertTrue(error(BmpTestData.valid(2, 2).signature('P', 'N').build()).contains("Firma"));
        assertTrue(error(BmpTestData.valid(2, 2).signature('b', 'm').build()).contains("Firma"));
    }

    @ParameterizedTest
    @CsvSource({"12", "108", "124", "0", "41"})
    void dibSizeOtherThan40(long dib) {
        assertTrue(error(BmpTestData.valid(2, 2).dibSize(dib).build()).contains("DIB"));
    }

    @ParameterizedTest
    @CsvSource({"1", "4", "8", "16", "32", "0"})
    void depthOtherThan24(int bits) {
        assertTrue(error(BmpTestData.valid(2, 2).bits(bits).build()).contains("bits por píxel"));
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "3", "6", "4294967295"})
    void compressionOtherThanZero(long compression) {
        assertTrue(error(BmpTestData.valid(2, 2).compression(compression).build()).contains("compresión"));
    }

    @ParameterizedTest
    @CsvSource({"0", "2", "65535"})
    void planesOtherThanOne(int planes) {
        assertTrue(error(BmpTestData.valid(2, 2).planes(planes).build()).contains("Planos"));
    }

    @ParameterizedTest
    @CsvSource({"0,2", "-1,2", "-2147483648,2", "2,0", "0,0", "0,-3"})
    void invalidDimensions(int width, int height) {
        String message = error(BmpTestData.valid(width, height).bodyLength(64).build());
        assertTrue(message.contains("Ancho inválido") || message.contains("Alto inválido"), message);
    }

    @Test
    void offsetBelow54IsRejected() {
        assertTrue(error(BmpTestData.valid(2, 2).offset(40).build()).contains("Offset"));
        assertTrue(error(BmpTestData.valid(2, 2).offset(0).build()).contains("Offset"));
    }

    @Test
    void offsetAbove2GiBIsNotConfusedWithNegative() {
        // uint32 0xFFFFFFFF debe leerse como 4294967295, no como -1
        String message = error(BmpTestData.valid(2, 2).offset(0xFFFFFFFFL).build());
        assertTrue(message.contains("truncado") && message.contains("4294967295"), message);
    }

    @Test
    void hugeDimensionsAreRejectedWithoutOverflow() {
        // 2^31-1 x 2^31-1: el cuerpo superaría un long si no se detectara
        byte[] data = BmpTestData.valid(Integer.MAX_VALUE, Integer.MAX_VALUE).bodyLength(16).build();
        String message = error(data);
        assertTrue(message.contains("fuera de rango"), message);
    }

    @Test
    void hugeNegativeHeightAndMinValueAreHandled() {
        byte[] minHeight = BmpTestData.valid(2, Integer.MIN_VALUE).bodyLength(16).build();
        assertTrue(error(minHeight).contains("truncado"));
        byte[] wide = BmpTestData.valid(Integer.MAX_VALUE, Integer.MIN_VALUE).bodyLength(16).build();
        assertTrue(error(wide).contains("fuera de rango"));
    }

    @Test
    void largeButRepresentableBodyIsTruncatedNotAccepted() {
        byte[] data = BmpTestData.valid(100_000, 100_000).bodyLength(16).build();
        assertTrue(error(data).contains("truncado"));
    }

    @Test
    void bodyIsReturnedAsCopyAndWithBodyDoesNotMutateOriginal() throws Exception {
        byte[] data = BmpTestData.valid(2, 2).gap(3).trailing(2).build();
        BmpImage img = parse(data.clone());
        byte[] body = img.copyBody();
        body[0] ^= 0xFF;
        assertNotSame(body, img.copyBody());
        assertArrayEquals(Arrays.copyOfRange(data, 57, 73), img.copyBody());

        BmpImage changed = img.withBody(body);
        byte[] out = changed.toBytes();
        assertEquals((byte) (data[57] ^ 0xFF), out[57]);
        out[57] = data[57];
        assertArrayEquals(data, out);
        assertArrayEquals(data, img.toBytes());
    }

    @Test
    void withBodyRejectsWrongLength() throws Exception {
        BmpImage img = parse(BmpTestData.valid(2, 2).build());
        assertThrows(IllegalArgumentException.class, () -> img.withBody(new byte[15]));
    }
}
