package ar.edu.itba.stegobmp.crypto;

/** Falla al descifrar. Las subclases distinguen padding inválido y ciphertext incompatible. */
public class DecryptionException extends CryptoException {

    private static final long serialVersionUID = 1L;

    public DecryptionException(String message) {
        super(message);
    }

    public DecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
