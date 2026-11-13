package ar.edu.itba.stegobmp.app;

import ar.edu.itba.stegobmp.payload.ByteSource;
import ar.edu.itba.stegobmp.payload.PayloadFormatException;
import ar.edu.itba.stegobmp.payload.PayloadFormatException.Reason;
import ar.edu.itba.stegobmp.steganography.StegoDataException;
import ar.edu.itba.stegobmp.steganography.StegoReader;
import java.util.Objects;

/** Adaptador que deja leer un {@link StegoReader} como {@link ByteSource}; así Payload no conoce la esteganografía. */
public final class StegoReaderSource implements ByteSource {
    private final StegoReader reader;

    public StegoReaderSource(StegoReader reader) {
        this.reader = Objects.requireNonNull(reader);
    }

    @Override
    public int readByte() throws PayloadFormatException {
        try {
            return reader.readByte();
        } catch (StegoDataException e) {
            throw new PayloadFormatException(Reason.TRUNCATED, e.getMessage());
        }
    }

    @Override
    public byte[] readBytes(int count) throws PayloadFormatException {
        try {
            return reader.readBytes(count);
        } catch (StegoDataException e) {
            throw new PayloadFormatException(Reason.TRUNCATED, e.getMessage());
        }
    }

    @Override
    public long remainingBytes() {
        return reader.remainingBytes();
    }
}
