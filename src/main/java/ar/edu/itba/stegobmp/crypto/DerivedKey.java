package ar.edu.itba.stegobmp.crypto;

import java.util.Arrays;

/**
 * Clave e IV derivados. Los arreglos internos se ponen a cero en {@link #close()}. {@link #keyBytes()} e
 * {@link #ivBytes()} devuelven copias: quien las recibe debe ponerlas a cero apenas termine de usarlas.
 */
public final class DerivedKey implements AutoCloseable {

    private final byte[] key;
    private final byte[] iv;
    private boolean closed;

    private DerivedKey(byte[] key, byte[] iv) {
        this.key = key;
        this.iv = iv;
    }

    /** Copia la clave y el IV (para ECB, {@code iv} es un arreglo vacío). */
    public static DerivedKey of(byte[] key, byte[] iv) throws InvalidCryptoParametersException {
        if (key == null || iv == null) {
            throw new InvalidCryptoParametersException("La clave y el IV no pueden ser nulos.");
        }
        if (key.length == 0) {
            throw new InvalidCryptoParametersException("La clave no puede estar vacía.");
        }
        return new DerivedKey(key.clone(), iv.clone());
    }

    /** Equivalente a {@code of(key, new byte[0])}, para ECB. */
    public static DerivedKey of(byte[] key) throws InvalidCryptoParametersException {
        return of(key, new byte[0]);
    }

    public synchronized byte[] keyBytes() {
        ensureOpen();
        return key.clone();
    }

    public synchronized byte[] ivBytes() {
        ensureOpen();
        return iv.clone();
    }

    public synchronized int keyLength() {
        ensureOpen();
        return key.length;
    }

    public synchronized int ivLength() {
        ensureOpen();
        return iv.length;
    }

    @Override
    public synchronized void close() {
        Arrays.fill(key, (byte) 0);
        Arrays.fill(iv, (byte) 0);
        closed = true;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("DerivedKey ya fue cerrada.");
        }
    }

    @Override
    public String toString() {
        return "DerivedKey[key=" + (closed ? "cerrada" : "oculta") + "]";
    }
}
