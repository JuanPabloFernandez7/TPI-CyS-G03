package ar.edu.itba.stegobmp.crypto;

import java.util.Arrays;

/**
 * {@link PayloadCipher} basado en contraseña. Deriva la clave en cada operación (unos ms) y la cierra al terminar,
 * de modo que la instancia es reutilizable y segura entre hilos. Guarda una copia de la contraseña hasta {@link #close()}.
 */
public final class PasswordPayloadCipher implements PayloadCipher, AutoCloseable {

    private final CipherSpec spec;
    private final char[] password;
    private final KeyDerivation derivation;
    private volatile boolean closed;

    public PasswordPayloadCipher(CipherSpec spec, char[] password) throws InvalidCryptoParametersException {
        this(spec, password, new Pbkdf2KeyDerivation());
    }

    public PasswordPayloadCipher(CipherSpec spec, char[] password, KeyDerivation derivation)
            throws InvalidCryptoParametersException {
        if (spec == null || password == null || derivation == null) {
            throw new InvalidCryptoParametersException(
                    "La especificación, la contraseña y la derivación de clave no pueden ser nulas.");
        }
        this.spec = spec;
        this.password = password.clone();
        this.derivation = derivation;
    }

    public CipherSpec spec() {
        return spec;
    }

    /** Longitud del ciphertext que producirá {@link #encrypt} para un plaintext de esa longitud. */
    public long ciphertextLength(long plaintextLength) {
        return spec.ciphertextLength(plaintextLength);
    }

    @Override
    public byte[] encrypt(byte[] plaintext) throws CryptoException {
        try (DerivedKey key = deriveKey()) {
            return SymmetricCipher.encrypt(spec, key, plaintext);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext) throws CryptoException {
        try (DerivedKey key = deriveKey()) {
            return SymmetricCipher.decrypt(spec, key, ciphertext);
        }
    }

    private DerivedKey deriveKey() throws CryptoException {
        if (closed) {
            throw new IllegalStateException("PasswordPayloadCipher ya fue cerrado.");
        }
        return derivation.derive(password, spec);
    }

    /** Pone a cero la copia de la contraseña. No debe llamarse mientras haya operaciones en curso. */
    @Override
    public void close() {
        closed = true;
        Arrays.fill(password, '\0');
    }

    @Override
    public String toString() {
        return "PasswordPayloadCipher[" + spec.description() + "]";
    }
}
