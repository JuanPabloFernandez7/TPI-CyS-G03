package ar.edu.itba.stegobmp.payload;

import java.util.Arrays;
import java.util.Objects;

/** {@link ByteSource} sobre un arreglo de bytes. */
public final class ByteArraySource implements ByteSource {
    private final byte[] data;
    private int position;

    public ByteArraySource(byte[] data) {
        this.data = Objects.requireNonNull(data, "data").clone();
    }

    @Override
    public int readByte() throws PayloadFormatException {
        if (position >= data.length) {
            throw new PayloadFormatException(PayloadFormatException.Reason.TRUNCATED,
                    "Payload truncado: no quedan bytes por leer.");
        }
        return data[position++] & 0xFF;
    }

    @Override
    public byte[] readBytes(int count) throws PayloadFormatException {
        if (count < 0) {
            throw new IllegalArgumentException("count negativo: " + count);
        }
        if (count > remainingBytes()) {
            throw new PayloadFormatException(PayloadFormatException.Reason.TRUNCATED,
                    "Payload truncado: se pidieron " + count + " bytes y quedan " + remainingBytes() + ".");
        }
        byte[] out = Arrays.copyOfRange(data, position, position + count);
        position += count;
        return out;
    }

    @Override
    public long remainingBytes() {
        return data.length - position;
    }
}
