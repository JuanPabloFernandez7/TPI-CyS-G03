package ar.edu.itba.stegobmp.crypto;

/** Base de los errores de la capa criptográfica. Los mensajes nunca incluyen contraseñas, claves ni IV. */
public class CryptoException extends Exception {

    private static final long serialVersionUID = 1L;

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
