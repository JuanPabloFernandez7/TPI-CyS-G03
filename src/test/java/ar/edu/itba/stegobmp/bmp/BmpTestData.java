package ar.edu.itba.stegobmp.bmp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/** Construye BMP sintéticos (little endian) para pruebas; permite corromper cada campo. */
public final class BmpTestData {
    long offset = 54;
    long dibSize = 40;
    int width = 2;
    int height = 2;
    int planes = 1;
    int bits = 24;
    long compression = 0;
    long sizeImage = 0;
    int gap = 0;
    int trailing = 0;
    byte[] signature = {'B', 'M'};
    Integer bodyLengthOverride = null;
    long seed = 1;

    public static BmpTestData valid(int width, int height) {
        BmpTestData d = new BmpTestData();
        d.width = width;
        d.height = height;
        return d;
    }

    BmpTestData offset(long v) {
        offset = v;
        return this;
    }

    BmpTestData dibSize(long v) {
        dibSize = v;
        return this;
    }

    BmpTestData planes(int v) {
        planes = v;
        return this;
    }

    BmpTestData bits(int v) {
        bits = v;
        return this;
    }

    BmpTestData compression(long v) {
        compression = v;
        return this;
    }

    BmpTestData sizeImage(long v) {
        sizeImage = v;
        return this;
    }

    public BmpTestData gap(int v) {
        gap = v;
        offset = 54 + v;
        return this;
    }

    public BmpTestData trailing(int v) {
        trailing = v;
        return this;
    }

    BmpTestData signature(char a, char b) {
        signature = new byte[] {(byte) a, (byte) b};
        return this;
    }

    BmpTestData bodyLength(int v) {
        bodyLengthOverride = v;
        return this;
    }

    static int stride(int width) {
        return (width * 3 + 3) / 4 * 4;
    }

    int expectedBodyLength() {
        return stride(width) * Math.abs(height);
    }

    public byte[] build() {
        int body = bodyLengthOverride != null ? bodyLengthOverride : expectedBodyLength();
        int total = 54 + gap + body + trailing;
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        b.put(signature);
        b.putInt(total);
        b.putInt(0);
        b.putInt((int) offset);
        b.putInt((int) dibSize);
        b.putInt(width);
        b.putInt(height);
        b.putShort((short) planes);
        b.putShort((short) bits);
        b.putInt((int) compression);
        b.putInt((int) sizeImage);
        b.putInt(2835);
        b.putInt(2835);
        b.putInt(0);
        b.putInt(0);
        byte[] rest = new byte[total - 54];
        new Random(seed).nextBytes(rest);
        b.put(rest);
        return b.array();
    }
}
