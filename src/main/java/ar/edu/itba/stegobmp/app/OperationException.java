package ar.edu.itba.stegobmp.app;

/** Falla de EMBED o EXTRACT con un mensaje apto para mostrar al usuario (nunca incluye la contraseña). */
public final class OperationException extends Exception {
    private static final long serialVersionUID = 1L;

    public OperationException(String message) {
        super(message);
    }

    public OperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
