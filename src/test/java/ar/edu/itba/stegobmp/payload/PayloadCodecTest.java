package ar.edu.itba.stegobmp.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ar.edu.itba.stegobmp.payload.PayloadFormatException.Reason;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PayloadCodecTest {

    private static byte[] hex(String s) {
        String[] parts = s.trim().split("\\s+");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    private static HiddenFile file(byte[] data, String ext) throws Exception {
        return new HiddenFile(data, ext.isEmpty() ? FileExtension.none() : FileExtension.of(ext));
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        byte[] out = new byte[n];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private static Reason readReason(byte[] stream) {
        return assertThrows(PayloadFormatException.class,
                () -> PayloadCodec.readFile(new ByteArraySource(stream))).reason();
    }

    private static Reason parseReason(byte[] plaintext) {
        return assertThrows(PayloadFormatException.class, () -> PayloadCodec.parseFile(plaintext)).reason();
    }

    // --- Serialización: vectores calculados a mano ---

    @Test
    void oneByteWithTxt() throws Exception {
        assertArrayEquals(hex("00 00 00 01 41 2E 74 78 74 00"),
                PayloadCodec.serializeFile(file(new byte[] {0x41}, ".txt")));
    }

    @Test
    void emptyFileWithExtension() throws Exception {
        byte[] expected = hex("00 00 00 00 2E 74 78 74 00");
        assertArrayEquals(expected, PayloadCodec.serializeFile(file(new byte[0], ".txt")));
        HiddenFile read = PayloadCodec.readFile(new ByteArraySource(expected));
        assertEquals(0, read.dataLength());
        assertEquals(".txt", read.extension().text());
    }

    @Test
    void fileWithoutExtensionUsesDotNul() throws Exception {
        byte[] expected = hex("00 00 00 02 01 02 2E 00");
        assertArrayEquals(expected, PayloadCodec.serializeFile(file(new byte[] {1, 2}, "")));
        HiddenFile read = PayloadCodec.parseFile(expected);
        assertEquals(FileExtension.none(), read.extension());
        assertArrayEquals(new byte[] {1, 2}, read.copyData());
    }

    @Test
    void emptyFileWithoutExtension() throws Exception {
        byte[] expected = hex("00 00 00 00 2E 00");
        assertArrayEquals(expected, PayloadCodec.serializeFile(file(new byte[0], "")));
        assertEquals(new HiddenFile(new byte[0], FileExtension.none()), PayloadCodec.parseFile(expected));
    }

    @Test
    void assignmentExample17118() throws Exception {
        byte[] stream = PayloadCodec.serializeFile(file(new byte[17118], ".png"));
        assertArrayEquals(hex("00 00 42 DE"), Arrays.copyOfRange(stream, 0, 4));
        assertEquals(4 + 17118 + 5, stream.length);
        assertArrayEquals(hex("2E 70 6E 67 00"), Arrays.copyOfRange(stream, stream.length - 5, stream.length));
    }

    @Test
    void sizeIsBigEndian() throws Exception {
        long[] sizes = {255, 256, 65535, 65536, 1 << 24};
        String[] prefixes = {"00 00 00 FF", "00 00 01 00", "00 00 FF FF", "00 01 00 00", "01 00 00 00"};
        for (int i = 0; i < sizes.length; i++) {
            byte[] stream = PayloadCodec.serializeFile(file(new byte[(int) sizes[i]], ".a"));
            assertArrayEquals(hex(prefixes[i]), Arrays.copyOfRange(stream, 0, 4), "size " + sizes[i]);
        }
    }

    @Test
    void consignaExample414WithTxt() throws Exception {
        byte[] stream = PayloadCodec.serializeFile(file(new byte[414], ".txt"));
        assertEquals(423, stream.length);
        assertArrayEquals(hex("00 00 01 9E"), Arrays.copyOfRange(stream, 0, 4));
    }

    @Test
    void ciphertextFrameKeepsCiphertextSizeDistinctFromFileSize() throws Exception {
        byte[] ciphertext = new byte[432];
        for (int i = 0; i < ciphertext.length; i++) {
            ciphertext[i] = (byte) (i % 255 + 1);
        }
        byte[] framed = PayloadCodec.frameCiphertext(ciphertext);
        assertEquals(436, framed.length);
        assertArrayEquals(hex("00 00 01 B0"), Arrays.copyOfRange(framed, 0, 4));
        assertArrayEquals(ciphertext, Arrays.copyOfRange(framed, 4, 436));

        // F = 414 (00 00 01 9E) dentro del plaintext; C = 432 (00 00 01 B0) en el marco
        byte[] plaintext = PayloadCodec.serializeFile(file(new byte[414], ".txt"));
        assertEquals(414, PayloadCodec.parseFile(plaintext).dataLength());
        assertArrayEquals(ciphertext, PayloadCodec.readCiphertextBlock(new ByteArraySource(framed)));
    }

    @Test
    void differentExtensionLengths() throws Exception {
        String longExt = "." + "x".repeat(254);
        for (String ext : new String[] {".c", ".txt", ".html", ".jpeg", longExt}) {
            HiddenFile f = file(new byte[] {7}, ext);
            byte[] stream = PayloadCodec.serializeFile(f);
            assertEquals(4 + 1 + ext.length() + 1, stream.length);
            assertEquals(0, stream[stream.length - 1]);
            assertEquals(f, PayloadCodec.parseFile(stream));
        }
    }

    @Test
    void dataContainingNulAndDotsIsReadBySize() throws Exception {
        byte[] data = {0x00, 0x2E, 0x00, 0x2E, 0x74, 0x00};
        HiddenFile f = file(data, ".bin");
        assertEquals(f, PayloadCodec.parseFile(PayloadCodec.serializeFile(f)));
    }

    @Test
    void allByteValuesAndRandomData() throws Exception {
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        HiddenFile f = file(all, ".dat");
        assertEquals(f, PayloadCodec.parseFile(PayloadCodec.serializeFile(f)));

        byte[] random = new byte[10_000];
        new Random(42).nextBytes(random);
        HiddenFile g = file(random, ".bmp");
        assertEquals(g, PayloadCodec.readFile(new ByteArraySource(PayloadCodec.serializeFile(g))));
    }

    @Test
    void roundTripWithAndWithoutExtension() throws Exception {
        for (String ext : new String[] {".txt", ".gz", ""}) {
            HiddenFile f = file("hola mundo".getBytes(), ext);
            assertEquals(f, PayloadCodec.parseFile(PayloadCodec.serializeFile(f)));
        }
    }

    @Test
    void streamLengthIs4PlusFPlusE() throws Exception {
        for (int f = 0; f <= 300; f++) {
            for (int extChars = 0; extChars <= 6; extChars++) {
                String ext = extChars == 0 ? "" : "." + "e".repeat(extChars);
                HiddenFile file = file(new byte[f], ext);
                int e = file.extension().encodedLength();
                assertEquals(4L + f + e, PayloadCodec.plainStreamLength(file));
                assertEquals(4 + f + e, PayloadCodec.serializeFile(file).length);
            }
        }
    }

    // --- Lectura: truncamiento e inconsistencias ---

    @Test
    void streamsShorterThanFourBytesAreTruncated() {
        for (int n = 0; n < 4; n++) {
            assertEquals(Reason.TRUNCATED, readReason(new byte[n]));
        }
    }

    @Test
    void declaredSizeLargerThanAvailable() {
        assertEquals(Reason.SIZE_EXCEEDS_AVAILABLE, readReason(hex("00 00 00 0A 01 02 03 04 05 06 07 08 09")));
    }

    @Test
    void missingExtensionIsTruncated() {
        assertEquals(Reason.TRUNCATED, readReason(hex("00 00 00 02 01 02")));
        assertEquals(Reason.TRUNCATED, readReason(hex("00 00 00 00")));
    }

    @Test
    void exactFitLeavesNothingRemaining() throws Exception {
        ByteArraySource source = new ByteArraySource(hex("00 00 00 01 41 2E 74 00"));
        PayloadCodec.readFile(source);
        assertEquals(0, source.remainingBytes());
    }

    @Test
    void missingNulIsUnterminated() {
        assertEquals(Reason.EXTENSION_UNTERMINATED, readReason(hex("00 00 00 01 41 2E 74 78 74")));
        assertEquals(Reason.EXTENSION_UNTERMINATED, readReason(hex("00 00 00 01 41 2E")));
    }

    @Test
    void extensionMustStartWithDot() {
        assertEquals(Reason.EXTENSION_MISSING_DOT, readReason(hex("00 00 00 01 41 74 78 74 00")));
        assertEquals(Reason.EXTENSION_MISSING_DOT, readReason(hex("00 00 00 01 41 00")));
    }

    @Test
    void invalidExtensionCharacters() {
        String[] bad = {"2E 07 00", "2E C3 A9 00", "2E 2F 74 00", "2E 5C 74 00", "2E 7F 00", "2E 74 FF 00"};
        for (String ext : bad) {
            byte[] stream = concat(hex("00 00 00 01 41"), hex(ext));
            assertEquals(Reason.EXTENSION_INVALID_CHAR, readReason(stream), ext);
        }
    }

    @Test
    void spaceInExtensionIsAccepted() throws Exception {
        HiddenFile f = PayloadCodec.parseFile(hex("00 00 00 00 2E 74 61 72 20 67 7A 00"));
        assertEquals(".tar gz", f.extension().text());
    }

    @Test
    void invalidSizesAreRejectedWithoutAllocating() {
        assertEquals(Reason.SIZE_INVALID, readReason(hex("80 00 00 00 2E 00")));
        assertEquals(Reason.SIZE_INVALID, readReason(hex("FF FF FF FF 2E 00")));
        assertEquals(Reason.SIZE_EXCEEDS_AVAILABLE, readReason(hex("7F FF FF FF 2E 00")));
    }

    @Test
    void readFileDoesNotOverRead() throws Exception {
        byte[] stream = concat(hex("00 00 00 01 41 2E 74 00"), new byte[20]);
        ByteArraySource source = new ByteArraySource(stream);
        PayloadCodec.readFile(source);
        assertEquals(20, source.remainingBytes());
    }

    // --- A5: sobrantes tras el NUL ---

    @Test
    void readFileStopsAtNulAndIgnoresTheRest() throws Exception {
        byte[] stream = concat(hex("00 00 00 01 41 2E 74 00"), hex("DE AD BE EF 00 11"));
        ByteArraySource source = new ByteArraySource(stream);
        HiddenFile f = PayloadCodec.readFile(source);
        assertEquals(file(new byte[] {0x41}, ".t"), f);
        assertEquals(6, source.remainingBytes());
    }

    @Test
    void parseFileRejectsTrailingBytes() throws Exception {
        byte[] exact = hex("00 00 00 01 41 2E 74 00");
        PayloadCodec.parseFile(exact);
        assertEquals(Reason.TRAILING_BYTES, parseReason(concat(exact, new byte[] {0})));
        assertEquals(Reason.TRAILING_BYTES, parseReason(concat(exact, hex("DE AD"))));
    }

    @Test
    void parseFileReportsOtherErrorsNormally() {
        assertEquals(Reason.TRUNCATED, parseReason(new byte[2]));
        assertEquals(Reason.EXTENSION_UNTERMINATED, parseReason(hex("00 00 00 00 2E 74")));
    }

    // --- Marco cifrado ---

    @Test
    void emptyCiphertextIsRejected() {
        assertEquals(Reason.CIPHERTEXT_SIZE_ZERO,
                assertThrows(PayloadFormatException.class, () -> PayloadCodec.frameCiphertext(new byte[0])).reason());
        assertEquals(Reason.CIPHERTEXT_SIZE_ZERO, assertThrows(PayloadFormatException.class,
                () -> PayloadCodec.readCiphertextBlock(new ByteArraySource(hex("00 00 00 00 01")))).reason());
    }

    @Test
    void ciphertextBlockValidatesSizeBeforeReading() throws Exception {
        assertEquals(Reason.SIZE_EXCEEDS_AVAILABLE, assertThrows(PayloadFormatException.class,
                () -> PayloadCodec.readCiphertextBlock(new ByteArraySource(hex("7F FF FF FF 01 02")))).reason());
        assertEquals(Reason.SIZE_INVALID, assertThrows(PayloadFormatException.class,
                () -> PayloadCodec.readCiphertextBlock(new ByteArraySource(hex("80 00 00 00 01")))).reason());
        assertEquals(Reason.TRUNCATED, assertThrows(PayloadFormatException.class,
                () -> PayloadCodec.readCiphertextBlock(new ByteArraySource(hex("00 00 01")))).reason());
        assertEquals(Reason.SIZE_EXCEEDS_AVAILABLE, assertThrows(PayloadFormatException.class,
                () -> PayloadCodec.readCiphertextBlock(new ByteArraySource(hex("00 00 00 05 01 02 03 04")))).reason());

        ByteArraySource exact = new ByteArraySource(hex("00 00 00 03 AA BB CC DD"));
        assertArrayEquals(hex("AA BB CC"), PayloadCodec.readCiphertextBlock(exact));
        assertEquals(1, exact.remainingBytes());
    }

    // --- Límites de longitud ---

    @Test
    void lengthLimits() throws Exception {
        long max = Integer.MAX_VALUE;
        assertEquals(max, PayloadCodec.plainStreamLength(max - 4 - 5, 5));
        assertEquals(Reason.FILE_TOO_LARGE, assertThrows(PayloadFormatException.class,
                () -> PayloadCodec.plainStreamLength(max - 4 - 5 + 1, 5)).reason());
        assertEquals(Reason.FILE_TOO_LARGE, assertThrows(PayloadFormatException.class,
                () -> PayloadCodec.plainStreamLength(max, 2)).reason());

        assertEquals(max, PayloadCodec.framedLength(max - 4));
        assertEquals(Reason.FILE_TOO_LARGE, assertThrows(PayloadFormatException.class,
                () -> PayloadCodec.framedLength(max - 3)).reason());
        assertEquals(Reason.CIPHERTEXT_SIZE_ZERO, assertThrows(PayloadFormatException.class,
                () -> PayloadCodec.framedLength(0)).reason());
        assertEquals(4 + 432L, PayloadCodec.framedLength(432));
        assertThrows(IllegalArgumentException.class, () -> PayloadCodec.framedLength(-1));
        assertThrows(IllegalArgumentException.class, () -> PayloadCodec.plainStreamLength(-1, 5));
    }

    @Test
    void hiddenFileIsImmutableAndComparesByContent() throws Exception {
        byte[] data = {1, 2, 3};
        HiddenFile f = file(data, ".x");
        data[0] = 99;
        assertArrayEquals(new byte[] {1, 2, 3}, f.copyData());
        f.copyData()[0] = 99;
        assertArrayEquals(new byte[] {1, 2, 3}, f.copyData());
        assertEquals(f, file(new byte[] {1, 2, 3}, ".x"));
        assertEquals(f.hashCode(), file(new byte[] {1, 2, 3}, ".x").hashCode());
    }

    @Test
    void byteArraySourceBehaviour() throws Exception {
        ByteArraySource s = new ByteArraySource(hex("01 02 03"));
        assertEquals(3, s.remainingBytes());
        assertEquals(1, s.readByte());
        assertThrows(PayloadFormatException.class, () -> s.readBytes(3));
        assertEquals(2, s.remainingBytes(), "una lectura fallida no consume");
        assertThrows(PayloadFormatException.class, () -> s.readBytes(Integer.MAX_VALUE));
        assertArrayEquals(hex("02 03"), s.readBytes(2));
        assertEquals(Reason.TRUNCATED, assertThrows(PayloadFormatException.class, s::readByte).reason());
        assertThrows(IllegalArgumentException.class, () -> s.readBytes(-1));
    }
}
