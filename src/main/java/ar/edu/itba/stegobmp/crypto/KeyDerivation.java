package ar.edu.itba.stegobmp.crypto;

/** Deriva la clave y el IV de una contraseña para un {@link CipherSpec}. */
public interface KeyDerivation {

    /** El llamador es dueño de la {@code DerivedKey} devuelta y debe cerrarla. */
    DerivedKey derive(char[] password, CipherSpec spec) throws CryptoException;
}
