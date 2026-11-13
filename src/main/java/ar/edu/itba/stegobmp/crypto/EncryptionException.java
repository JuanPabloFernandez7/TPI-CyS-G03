package ar.edu.itba.stegobmp.crypto;

/** Falla de JCE al cifrar. */
public class EncryptionException extends CryptoException {

    private static final long serialVersionUID = 1L;

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
