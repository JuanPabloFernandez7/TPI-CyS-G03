package ar.edu.itba.stegobmp.steganography;

/** Se intentó leer más datos de los que el portador puede contener. */
public final class StegoDataException extends Exception {
    public StegoDataException(String message) {
        super(message);
    }
}
