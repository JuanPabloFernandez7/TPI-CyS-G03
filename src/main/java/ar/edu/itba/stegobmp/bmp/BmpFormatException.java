package ar.edu.itba.stegobmp.bmp;

/** El archivo no es un BMP V3/V4/V5 de 24 bits sin compresión válido. */
public final class BmpFormatException extends Exception {
    public BmpFormatException(String message) {
        super(message);
    }
}
