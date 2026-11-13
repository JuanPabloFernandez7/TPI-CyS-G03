package ar.edu.itba.stegobmp.crypto;

/** Parámetros nulos o incorrectos (contraseña no codificable, clave o IV de longitud errónea, combinación no soportada). */
public class InvalidCryptoParametersException extends CryptoException {

    private static final long serialVersionUID = 1L;

    public InvalidCryptoParametersException(String message) {
        super(message);
    }

    public InvalidCryptoParametersException(String message, Throwable cause) {
        super(message, cause);
    }
}
