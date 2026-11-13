package ar.edu.itba.stegobmp.payload;

/**
 * Lectura secuencial de bytes, propia de Payload. {@link #remainingBytes()} es la capacidad de
 * lectura restante, no la longitud del mensaje.
 */
public interface ByteSource {

    /** Siguiente byte (0..255). Lanza {@code TRUNCATED} si no queda ninguno. */
    int readByte() throws PayloadFormatException;

    /** Los siguientes {@code count} bytes; lanza {@code TRUNCATED} sin reservar memoria si no hay tantos. */
    byte[] readBytes(int count) throws PayloadFormatException;

    long remainingBytes();
}
