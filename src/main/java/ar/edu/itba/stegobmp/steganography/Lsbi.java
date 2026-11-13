package ar.edu.itba.stegobmp.steganography;

import java.util.Objects;

/**
 * LSBI over B and G components in physical BGR order. The first four body bytes store
 * the per-pattern inversion flags and are not used for payload data.
 */
public final class Lsbi implements Steganography {
    private static final int FLAG_BYTES = 4;
    private static final int KEEP_HIGH_BITS = 0xFE;

    private final BitOrder bitOrder;

    public Lsbi() {
        this(BitOrder.DEFAULT);
    }

    public Lsbi(BitOrder bitOrder) {
        this.bitOrder = Objects.requireNonNull(bitOrder);
    }

    @Override
    public long capacityBytes(int bodyLength) {
        throw new UnsupportedOperationException("LSBI requiere CarrierLayout para calcular la capacidad");
    }

    @Override
    public byte[] embed(byte[] body, byte[] data) {
        throw new UnsupportedOperationException("LSBI requiere CarrierLayout para insertar datos");
    }

    @Override
    public StegoReader newReader(byte[] body) {
        throw new UnsupportedOperationException("LSBI requiere CarrierLayout para extraer datos");
    }

    @Override
    public long capacityBytes(int bodyLength, CarrierLayout layout) {
        validateLayout(bodyLength, layout);
        return layout.capacityBytesAfterPrefix(FLAG_BYTES);
    }

    @Override
    public byte[] embed(byte[] body, byte[] data, CarrierLayout layout) throws CapacityExceededException {
        Objects.requireNonNull(body);
        Objects.requireNonNull(data);
        validateLayout(body.length, layout);
        long capacity = capacityBytes(body.length, layout);
        if (data.length > capacity) {
            throw new CapacityExceededException(data.length, capacity);
        }

        long[] changed = new long[4];
        long[] unchanged = new long[4];
        int position = nextDataComponent(body, layout, FLAG_BYTES);
        for (byte value : data) {
            for (int bit = 0; bit < Byte.SIZE; bit++) {
                int componentPattern = patternOf(body[position] & 0xFF);
                int messageBit = bitOrder.bitOf(value & 0xFF, bit);
                if ((body[position] & 1) != messageBit) {
                    changed[componentPattern]++;
                } else {
                    unchanged[componentPattern]++;
                }
                position = nextDataComponent(body, layout, position + 1);
            }
        }

        boolean[] invert = new boolean[4];
        for (int pattern = 0; pattern < invert.length; pattern++) {
            invert[pattern] = shouldInvert(changed[pattern], unchanged[pattern]);
        }

        byte[] result = body.clone();
        for (int index = 0; index < Math.min(FLAG_BYTES, result.length); index++) {
            result[index] = (byte) ((result[index] & KEEP_HIGH_BITS) | (invert[index] ? 1 : 0));
        }

        position = nextDataComponent(body, layout, FLAG_BYTES);
        for (byte value : data) {
            for (int bit = 0; bit < Byte.SIZE; bit++) {
                int componentPattern = patternOf(body[position] & 0xFF);
                int messageBit = bitOrder.bitOf(value & 0xFF, bit);
                int encodedBit = messageBit ^ (invert[componentPattern] ? 1 : 0);
                result[position] = (byte) ((body[position] & KEEP_HIGH_BITS) | encodedBit);
                position = nextDataComponent(body, layout, position + 1);
            }
        }
        return result;
    }

    @Override
    public StegoReader newReader(byte[] body, CarrierLayout layout) {
        Objects.requireNonNull(body);
        validateLayout(body.length, layout);
        return new Reader(body, layout);
    }

    static int patternOf(int component) {
        return (component >>> 1) & 0b11;
    }

    static boolean shouldInvert(long changed, long unchanged) {
        return changed > unchanged;
    }

    private static int nextDataComponent(byte[] body, CarrierLayout layout, int start) {
        for (int index = start; index < body.length; index++) {
            if (layout.isBlueOrGreenComponent(index)) {
                return index;
            }
        }
        return body.length;
    }

    private static void validateLayout(int bodyLength, CarrierLayout layout) {
        Objects.requireNonNull(layout);
        if (bodyLength < 0 || layout.bodyLength() != bodyLength) {
            throw new IllegalArgumentException("La geometría no coincide con el cuerpo del portador");
        }
    }

    private final class Reader implements StegoReader {
        private final byte[] body;
        private final CarrierLayout layout;
        private final boolean[] invert = new boolean[4];
        private final long totalBytes;
        private long bytesRead;
        private int position;

        Reader(byte[] body, CarrierLayout layout) {
            this.body = body;
            this.layout = layout;
            this.totalBytes = capacityBytes(body.length, layout);
            for (int pattern = 0; pattern < invert.length && pattern < body.length; pattern++) {
                invert[pattern] = (body[pattern] & 1) != 0;
            }
            this.position = nextDataComponent(body, layout, FLAG_BYTES);
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
            int value = 0;
            for (int bit = 0; bit < Byte.SIZE; bit++) {
                int componentPattern = patternOf(body[position] & 0xFF);
                int decodedBit = (body[position] & 1) ^ (invert[componentPattern] ? 1 : 0);
                value = bitOrder.place(value, decodedBit, bit);
                position = nextDataComponent(body, layout, position + 1);
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
            for (int index = 0; index < count; index++) {
                out[index] = (byte) readByte();
            }
            return out;
        }
    }
}
