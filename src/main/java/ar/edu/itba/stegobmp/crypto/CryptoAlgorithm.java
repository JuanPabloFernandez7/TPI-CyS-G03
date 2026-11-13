package ar.edu.itba.stegobmp.crypto;

/** Algoritmos de la consigna con su nombre JCA, longitud de clave y tamaño de bloque (que es también el del IV). */
public enum CryptoAlgorithm {
    AES_128("AES-128", "AES", 16, 16),
    AES_192("AES-192", "AES", 24, 16),
    AES_256("AES-256", "AES", 32, 16),
    TRIPLE_DES("3DES", "DESede", 24, 8);

    private final String displayName;
    private final String jcaName;
    private final int keyBytes;
    private final int blockBytes;

    CryptoAlgorithm(String displayName, String jcaName, int keyBytes, int blockBytes) {
        this.displayName = displayName;
        this.jcaName = jcaName;
        this.keyBytes = keyBytes;
        this.blockBytes = blockBytes;
    }

    public String displayName() {
        return displayName;
    }

    public String jcaName() {
        return jcaName;
    }

    public int keyBytes() {
        return keyBytes;
    }

    public int blockBytes() {
        return blockBytes;
    }
}
