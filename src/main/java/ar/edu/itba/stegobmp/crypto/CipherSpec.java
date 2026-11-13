package ar.edu.itba.stegobmp.crypto;

import java.util.Objects;

/** Combinación algoritmo + modo y todo lo que se deriva de ella sin tocar JCE. */
public record CipherSpec(CryptoAlgorithm algorithm, CryptoMode mode) {

    public CipherSpec {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(mode, "mode");
    }

    public String transformation() {
        return algorithm.jcaName() + "/" + mode.jcaMode() + "/" + mode.padding();
    }

    public int blockBytes() {
        return algorithm.blockBytes();
    }

    public boolean isPadded() {
        return mode.isPadded();
    }

    public int ivBytes() {
        return mode.usesIv() ? algorithm.blockBytes() : 0;
    }

    /** Bytes que debe entregar PBKDF2: clave seguida del IV (nada de IV en ECB). */
    public int derivedBytes() {
        return algorithm.keyBytes() + ivBytes();
    }

    public String description() {
        return algorithm.displayName() + "/" + mode;
    }

    /**
     * Longitud del ciphertext para un plaintext de {@code plaintextLength} bytes: con padding PKCS5 es
     * {@code B * (floor(P / B) + 1)}; sin padding es {@code P}.
     *
     * @throws ArithmeticException si el resultado no entra en un {@code long}
     * @throws IllegalArgumentException si la longitud es negativa
     */
    public long ciphertextLength(long plaintextLength) {
        if (plaintextLength < 0) {
            throw new IllegalArgumentException("La longitud del plaintext no puede ser negativa: " + plaintextLength);
        }
        if (!isPadded()) {
            return plaintextLength;
        }
        long block = blockBytes();
        return Math.multiplyExact(Math.addExact(plaintextLength / block, 1L), block);
    }
}
