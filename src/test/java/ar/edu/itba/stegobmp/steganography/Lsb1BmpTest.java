package ar.edu.itba.stegobmp.steganography;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ar.edu.itba.stegobmp.bmp.BmpImage;
import ar.edu.itba.stegobmp.bmp.BmpReader;
import ar.edu.itba.stegobmp.bmp.BmpTestData;
import ar.edu.itba.stegobmp.bmp.BmpWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Lsb1BmpTest {
    private final Lsb1 lsb1 = new Lsb1();

    private static byte[] randomData(int n, long seed) {
        byte[] d = new byte[n];
        new Random(seed).nextBytes(d);
        return d;
    }

    private BmpImage embedInto(BmpImage image, byte[] data) throws Exception {
        return image.withBody(lsb1.embed(image.copyBody(), data));
    }

    private byte[] extract(BmpImage image, int n) throws Exception {
        return lsb1.newReader(image.copyBody()).readBytes(n);
    }

    /** Solo pueden diferir los LSB de los primeros 8*n bytes del cuerpo. */
    private void assertOnlyLsbsChanged(byte[] before, byte[] after, int bodyOffset, int messageBytes) {
        assertEquals(before.length, after.length);
        for (int k = 0; k < before.length; k++) {
            int diff = before[k] ^ after[k];
            boolean authorized = k >= bodyOffset && k < bodyOffset + 8L * messageBytes;
            if (authorized) {
                assertEquals(0, diff & 0xFE, "bits altos alterados en " + k);
            } else {
                assertEquals(0, diff, "byte no autorizado alterado en " + k);
            }
        }
    }

    // Caso 10: el padding forma parte del portador (ancho 3: stride 12, 9 bytes de píxel + 3 de padding)
    @Test
    void paddingBytesAreCarrier() throws Exception {
        byte[] file = BmpTestData.valid(3, 2).build();
        BmpImage image = BmpReader.parse(file);
        assertEquals(24, image.bodyLength());
        assertEquals(3, lsb1.capacityBytes(image.bodyLength()));

        byte[] data = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        BmpImage stego = embedInto(image, data);
        byte[] body = stego.copyBody();
        for (int k = 0; k < 24; k++) {
            assertEquals(1, body[k] & 1, "LSB " + k);
        }
        assertArrayEquals(data, extract(stego, 3));
        assertOnlyLsbsChanged(file, stego.toBytes(), image.bodyOffset(), 3);
        assertThrows(CapacityExceededException.class, () -> embedInto(image, new byte[4]));
    }

    // Caso 11: offset mayor que 54
    @Test
    void offsetGreaterThan54() throws Exception {
        byte[] file = BmpTestData.valid(8, 8).gap(10).build();
        BmpImage image = BmpReader.parse(file);
        assertEquals(64, image.bodyOffset());
        byte[] data = randomData(20, 1);
        BmpImage stego = embedInto(image, data);
        assertArrayEquals(data, extract(stego, 20));
        assertOnlyLsbsChanged(file, stego.toBytes(), 64, 20);
    }

    // Caso 12: bytes adicionales al final
    @Test
    void trailingBytesPreservedAndNotCarrier() throws Exception {
        byte[] file = BmpTestData.valid(4, 4).trailing(37).build();
        BmpImage image = BmpReader.parse(file);
        int capacity = (int) lsb1.capacityBytes(image.bodyLength());
        assertEquals(image.bodyLength() / 8, capacity);
        byte[] data = randomData(capacity, 2);
        BmpImage stego = embedInto(image, data);
        assertArrayEquals(data, extract(stego, capacity));
        assertOnlyLsbsChanged(file, stego.toBytes(), image.bodyOffset(), capacity);
        assertArrayEquals(image.trailingBytes(), stego.trailingBytes());
        assertThrows(CapacityExceededException.class, () -> embedInto(image, new byte[capacity + 1]));
    }

    // Alto negativo: orden físico sin invertir filas
    @Test
    void negativeHeightTraversedInPhysicalOrder() throws Exception {
        byte[] file = BmpTestData.valid(5, -4).build();
        BmpImage image = BmpReader.parse(file);
        byte[] data = randomData(image.bodyLength() / 8, 3);
        BmpImage stego = embedInto(image, data);
        assertArrayEquals(data, extract(stego, data.length));
        assertOnlyLsbsChanged(file, stego.toBytes(), image.bodyOffset(), data.length);
    }

    // Caso 13: cambios en el archivo completo, y escritura/lectura en disco
    @Test
    void diskRoundTripAndWholeFileDiff(@TempDir Path dir) throws Exception {
        byte[] file = BmpTestData.valid(16, 16).gap(6).trailing(5).build();
        BmpImage image = BmpReader.parse(file);
        byte[] data = randomData(90, 4);
        BmpImage stego = embedInto(image, data);

        Path out = dir.resolve("out.bmp");
        BmpWriter.write(stego, out);
        byte[] written = Files.readAllBytes(out);
        assertOnlyLsbsChanged(file, written, image.bodyOffset(), data.length);
        assertArrayEquals(data, extract(BmpReader.read(out), data.length));
    }
}
