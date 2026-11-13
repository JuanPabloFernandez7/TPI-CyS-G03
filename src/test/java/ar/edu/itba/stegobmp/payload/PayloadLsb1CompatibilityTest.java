package ar.edu.itba.stegobmp.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ar.edu.itba.stegobmp.bmp.BmpImage;
import ar.edu.itba.stegobmp.bmp.BmpReader;
import ar.edu.itba.stegobmp.bmp.BmpTestData;
import ar.edu.itba.stegobmp.steganography.Lsb1;
import ar.edu.itba.stegobmp.steganography.StegoDataException;
import ar.edu.itba.stegobmp.steganography.StegoReader;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Verifica que ByteSource encaja con StegoReader; el adaptador vive solo en el código de prueba. */
class PayloadLsb1CompatibilityTest {

    private static ByteSource adapt(StegoReader reader) {
        return new ByteSource() {
            @Override
            public int readByte() throws PayloadFormatException {
                try {
                    return reader.readByte();
                } catch (StegoDataException e) {
                    throw new PayloadFormatException(PayloadFormatException.Reason.TRUNCATED, e.getMessage());
                }
            }

            @Override
            public byte[] readBytes(int count) throws PayloadFormatException {
                try {
                    return reader.readBytes(count);
                } catch (StegoDataException e) {
                    throw new PayloadFormatException(PayloadFormatException.Reason.TRUNCATED, e.getMessage());
                }
            }

            @Override
            public long remainingBytes() {
                return reader.remainingBytes();
            }
        };
    }

    @Test
    void payloadSurvivesLsb1InsideBmp() throws Exception {
        byte[] data = new byte[300];
        new Random(5).nextBytes(data);
        HiddenFile file = new HiddenFile(data, FileExtension.of(".bin"));

        BmpImage image = BmpReader.parse(BmpTestData.valid(40, 30).gap(4).trailing(9).build());
        Lsb1 lsb1 = new Lsb1();
        BmpImage stego = image.withBody(lsb1.embed(image.copyBody(), PayloadCodec.serializeFile(file)));

        HiddenFile read = PayloadCodec.readFile(adapt(lsb1.newReader(stego.copyBody())));
        assertEquals(file, read);
    }

    @Test
    void framedCiphertextSurvivesLsb1() throws Exception {
        byte[] ciphertext = new byte[160];
        new Random(6).nextBytes(ciphertext);
        BmpImage image = BmpReader.parse(BmpTestData.valid(40, 30).build());
        Lsb1 lsb1 = new Lsb1();
        BmpImage stego = image.withBody(lsb1.embed(image.copyBody(), PayloadCodec.frameCiphertext(ciphertext)));

        byte[] read = PayloadCodec.readCiphertextBlock(adapt(lsb1.newReader(stego.copyBody())));
        assertArrayEquals(ciphertext, read);
    }
}
