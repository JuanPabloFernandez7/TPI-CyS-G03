package ar.edu.itba.stegobmp.steganography;

import java.util.Objects;

/**
 * LSB1: el bit {@code j} del byte de mensaje {@code i} sustituye el LSB del byte {@code 8*i + j} del
 * cuerpo, recorrido en orden físico (B, G, R y padding incluidos). Los bytes posteriores al mensaje
 * no se modifican.
 */
public final class Lsb1 implements Steganography {
    private static final int BITS_PER_BYTE = 8;
    private static final int KEEP_HIGH_BITS = 0xFE;

    private final BitOrder bitOrder;

    public Lsb1() {
        this(BitOrder.DEFAULT);
    }

    public Lsb1(BitOrder bitOrder) {
        this.bitOrder = Objects.requireNonNull(bitOrder);
    }

    @Override
    public long capacityBytes(int bodyLength) {
        if (bodyLength < 0) {
            throw new IllegalArgumentException("Largo de cuerpo negativo: " + bodyLength);
        }
        return bodyLength / BITS_PER_BYTE;
    }

    @Override
    public byte[] embed(byte[] body, byte[] data) throws CapacityExceededException {
        Objects.requireNonNull(body);
        Objects.requireNonNull(data);
        long capacity = capacityBytes(body.length);
        if (data.length > capacity) {
            throw new CapacityExceededException(data.length, capacity);
        }
        byte[] result = body.clone();
        int position = 0;
        for (byte value : data) {
            for (int bit = 0; bit < BITS_PER_BYTE; bit++) {
                result[position] = (byte) ((result[position] & KEEP_HIGH_BITS) | bitOrder.bitOf(value, bit));
                position++;
            }
        }
        return result;
    }

    @Override
    public StegoReader newReader(byte[] body) {
        return new Reader(Objects.requireNonNull(body));
    }

    private final class Reader implements StegoReader {
        private final byte[] body;
        private final long totalBytes;
        private long bytesRead;

        Reader(byte[] body) {
            this.body = body;
            this.totalBytes = capacityBytes(body.length);
        }

        @Override
        public long remainingBytes() {
            return totalBytes - bytesRead;
        }

        @Override
        public int readByte() throws StegoDataException {
            if (remainingBytes() < 1) {
                throw new StegoDataException("Datos insuficientes en el portador: se pidió 1 byte y no queda capacidad de lectura");
            }
            int position = (int) (bytesRead * BITS_PER_BYTE);
            int value = 0;
            for (int bit = 0; bit < BITS_PER_BYTE; bit++) {
                value = bitOrder.place(value, body[position + bit] & 1, bit);
            }
            bytesRead++;
            return value;
        }

        @Override
        public byte[] readBytes(int count) throws StegoDataException {
            if (count < 0) {
                throw new IllegalArgumentException("Cantidad negativa: " + count);
            }
            if (count > remainingBytes()) {
                throw new StegoDataException("Datos insuficientes en el portador: se pidieron " + count
                        + " bytes y quedan " + remainingBytes());
            }
            byte[] out = new byte[count];
            for (int i = 0; i < count; i++) {
                out[i] = (byte) readByte();
            }
            return out;
        }
    }
}
