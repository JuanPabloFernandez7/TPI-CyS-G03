package ar.edu.itba.stegobmp.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ar.edu.itba.stegobmp.payload.PayloadFormatException;
import ar.edu.itba.stegobmp.payload.PayloadFormatException.Reason;
import ar.edu.itba.stegobmp.steganography.Lsb1;
import org.junit.jupiter.api.Test;

class StegoReaderSourceTest {

    private static StegoReaderSource sourceOver(byte[] hidden, int bodyLength) throws Exception {
        Lsb1 lsb1 = new Lsb1();
        byte[] body = lsb1.embed(new byte[bodyLength], hidden);
        return new StegoReaderSource(lsb1.newReader(body));
    }

    @Test
    void readsBytesSequentiallyAndReportsRemainingCapacity() throws Exception {
        StegoReaderSource source = sourceOver(new byte[] {1, 2, 3}, 64);
        assertEquals(8, source.remainingBytes());
        assertEquals(1, source.readByte());
        assertArrayEquals(new byte[] {2, 3}, source.readBytes(2));
        assertEquals(5, source.remainingBytes());
    }

    @Test
    void exhaustedReaderIsReportedAsTruncatedPayload() throws Exception {
        StegoReaderSource source = sourceOver(new byte[] {1}, 16);
        source.readBytes(2);
        assertEquals(Reason.TRUNCATED, assertThrows(PayloadFormatException.class, source::readByte).reason());
        assertEquals(Reason.TRUNCATED, assertThrows(PayloadFormatException.class, () -> source.readBytes(1)).reason());
    }

    @Test
    void oversizedReadDoesNotAllocate() throws Exception {
        StegoReaderSource source = sourceOver(new byte[0], 16);
        assertEquals(Reason.TRUNCATED,
                assertThrows(PayloadFormatException.class, () -> source.readBytes(Integer.MAX_VALUE)).reason());
    }
}
