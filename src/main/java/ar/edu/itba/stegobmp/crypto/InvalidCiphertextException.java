package ar.edu.itba.stegobmp.crypto;

/** Ciphertext incompatible con el algoritmo o el modo (vacío o de longitud no múltiplo del bloque en ECB/CBC). */
public class InvalidCiphertextException extends DecryptionException {

    private static final long serialVersionUID = 1L;

    public InvalidCiphertextException(String message) {
        super(message);
    }

    public InvalidCiphertextException(String message, Throwable cause) {
        super(message, cause);
    }
}
