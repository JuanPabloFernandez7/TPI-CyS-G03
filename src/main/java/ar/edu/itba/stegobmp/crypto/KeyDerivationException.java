package ar.edu.itba.stegobmp.crypto;

/** Falla de PBKDF2 (por ejemplo, algoritmo no disponible en el runtime). */
public class KeyDerivationException extends CryptoException {

    private static final long serialVersionUID = 1L;

    public KeyDerivationException(String message, Throwable cause) {
        super(message, cause);
    }
}
