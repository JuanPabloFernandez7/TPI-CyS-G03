package ar.edu.itba.stegobmp.payload;

/** Error de formato del payload; {@link Reason} permite distinguir la causa sin leer el mensaje. */
public class PayloadFormatException extends Exception {

    public enum Reason {
        TRUNCATED,
        SIZE_INVALID,
        SIZE_EXCEEDS_AVAILABLE,
        CIPHERTEXT_SIZE_ZERO,
        EXTENSION_MISSING_DOT,
        EXTENSION_INVALID_CHAR,
        EXTENSION_UNTERMINATED,
        TRAILING_BYTES,
        FILE_TOO_LARGE
    }

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public PayloadFormatException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
