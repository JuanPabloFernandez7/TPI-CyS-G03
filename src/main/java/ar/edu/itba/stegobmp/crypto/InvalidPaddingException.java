package ar.edu.itba.stegobmp.crypto;

/** Padding PKCS5 inválido. Detecta solo algunos errores de contraseña: no sustituye una autenticación. */
public class InvalidPaddingException extends DecryptionException {

    private static final long serialVersionUID = 1L;

    public InvalidPaddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
