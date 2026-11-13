package ar.edu.itba.stegobmp.steganography;

/**
 * Lectura secuencial de los bytes ocultos en un cuerpo. El lector no sabe dónde termina el mensaje:
 * esa decisión (tamaño, extensión con NUL, etc.) es de la capa Payload.
 */
public interface StegoReader {

    /** Siguiente byte (0..255). Lanza {@link StegoDataException} si no queda capacidad de lectura. */
    int readByte() throws StegoDataException;

    /** Los siguientes {@code count} bytes. No reserva memoria si {@code count} excede lo disponible. */
    byte[] readBytes(int count) throws StegoDataException;

    /**
     * Bytes que todavía se pueden leer del cuerpo, es decir la capacidad restante. No es la cantidad
     * de bytes del mensaje: el cuerpo puede contener más espacio que el mensaje ocultado.
     */
    long remainingBytes();
}
