package ar.edu.itba.stegobmp.steganography;

import java.util.Objects;

/**
 * LSB4: cada byte del mensaje se transmite en dos grupos de cuatro bits, almacenados en los cuatro
 * bits menos significativos de dos bytes consecutivos del cuerpo.
 */
public final class Lsb4 implements Steganography {
    private static final int KEEP_HIGH_BITS = 0xF0;

    private final BitOrder bitOrder;

    public Lsb4() {
        this(BitOrder.DEFAULT);
    }

    public Lsb4(BitOrder bitOrder) {
        this.bitOrder = Objects.requireNonNull(bitOrder);
    }

    @Override
    public long capacityBytes(int bodyLength) {
        if (bodyLength < 0) {
            throw new IllegalArgumentException("Largo de cuerpo negativo: " + bodyLength);
        }
        return bodyLength / 2;
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
            for (int group = 0; group < 2; group++) {
                int nibble = 0;
                for (int bit = 0; bit < 4; bit++) {
                    int messageBit = group * 4 + bit;
                    int targetBit = bitOrder == BitOrder.MSB_FIRST ? 3 - bit : bit;
                    nibble |= bitOrder.bitOf(value, messageBit) << targetBit;
                }
                result[position] = (byte) ((result[position] & KEEP_HIGH_BITS) | nibble);
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
                throw new StegoDataException(
                        "Datos insuficientes en el portador: se pidió 1 byte y no queda capacidad de lectura");
            }

            int position = (int) (bytesRead * 2);
            int value = 0;
            for (int group = 0; group < 2; group++) {
                int nibble = body[position + group] & 0x0F;
                for (int bit = 0; bit < 4; bit++) {
                    int messageBit = group * 4 + bit;
                    int targetBit = bitOrder == BitOrder.MSB_FIRST ? 3 - bit : bit;
                    value = bitOrder.place(value, (nibble >> targetBit) & 1, messageBit);
                }
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
