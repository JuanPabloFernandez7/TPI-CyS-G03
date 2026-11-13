package ar.edu.itba.stegobmp.bmp;

import java.util.Arrays;
import java.util.Objects;

/**
 * BMP validado. Conserva el archivo completo; el cuerpo (píxeles más padding de fila) es la región
 * {@code [bodyOffset, bodyOffset + bodyLength)}. Lo anterior (header y bytes intermedios) y lo
 * posterior (bytes extra) se preservan pero no son portadores.
 */
public final class BmpImage {
    private final BmpHeader header;
    private final byte[] fileBytes;
    private final int bodyOffset;
    private final int bodyLength;

    BmpImage(BmpHeader header, byte[] fileBytes, int bodyOffset, int bodyLength) {
        this.header = Objects.requireNonNull(header);
        this.fileBytes = Objects.requireNonNull(fileBytes);
        this.bodyOffset = bodyOffset;
        this.bodyLength = bodyLength;
    }

    public BmpHeader header() {
        return header;
    }

    /** Posición del primer byte del cuerpo en el archivo (el offset real del header). */
    public int bodyOffset() {
        return bodyOffset;
    }

    /** Capacidad en bytes portadores: todo el cuerpo, padding de filas incluido. */
    public int bodyLength() {
        return bodyLength;
    }

    /** Bytes entre el header de 54 bytes y el cuerpo (vacío si el offset es 54). */
    public byte[] gapBytes() {
        return Arrays.copyOfRange(fileBytes, BmpHeader.SIZE, bodyOffset);
    }

    /** Bytes posteriores al cuerpo (vacío si no hay). */
    public byte[] trailingBytes() {
        return Arrays.copyOfRange(fileBytes, bodyOffset + bodyLength, fileBytes.length);
    }

    public int fileLength() {
        return fileBytes.length;
    }

    /** Copia del cuerpo en el orden físico del archivo. */
    public byte[] copyBody() {
        return Arrays.copyOfRange(fileBytes, bodyOffset, bodyOffset + bodyLength);
    }

    /** Devuelve una imagen nueva con el cuerpo reemplazado; todo lo demás queda idéntico. */
    public BmpImage withBody(byte[] newBody) {
        if (newBody.length != bodyLength) {
            throw new IllegalArgumentException(
                    "El cuerpo debe medir " + bodyLength + " bytes y mide " + newBody.length);
        }
        byte[] copy = fileBytes.clone();
        System.arraycopy(newBody, 0, copy, bodyOffset, bodyLength);
        return new BmpImage(header, copy, bodyOffset, bodyLength);
    }

    /** Copia del archivo completo, tal como se escribiría. */
    public byte[] toBytes() {
        return fileBytes.clone();
    }
}
