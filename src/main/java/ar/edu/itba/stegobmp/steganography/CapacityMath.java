package ar.edu.itba.stegobmp.steganography;

/**
 * Fórmulas de capacidad de LSB1 expresadas en bytes del flujo oculto. Un resultado negativo significa
 * que ni siquiera caben los metadatos. Informativo (mensajes de error y pruebas): {@code embed} valida
 * siempre el flujo real.
 */
public final class CapacityMath {
    private static final int SIZE_FIELD_BYTES = 4;

    private CapacityMath() {
    }

    /** {@code N_max = floor(bodyLength / 8)}. */
    public static long maxStreamBytes(long bodyLength) {
        return bodyLength / 8;
    }

    /**
     * Tamaño máximo de archivo sin cifrar: flujo = 4 + F + E.
     *
     * @param extensionBytes E = longitud de la extensión (con el punto) más 1 por el NUL final
     */
    public static long maxFileSizeUnencrypted(long bodyLength, int extensionBytes) {
        return maxStreamBytes(bodyLength) - SIZE_FIELD_BYTES - extensionBytes;
    }

    /**
     * Tamaño máximo de archivo cifrado en ECB/CBC con relleno PKCS5/PKCS7: flujo = 4 + T, con
     * T = B * (floor((4 + F + E) / B) + 1).
     */
    public static long maxFileSizePadded(long bodyLength, int extensionBytes, int blockSize) {
        long ciphertextRoom = maxStreamBytes(bodyLength) - SIZE_FIELD_BYTES;
        long blocks = Math.floorDiv(ciphertextRoom, (long) blockSize);
        return blocks * blockSize - 1 - SIZE_FIELD_BYTES - extensionBytes;
    }

    /** Tamaño máximo de archivo cifrado sin relleno (CFB/OFB): flujo = 4 + (4 + F + E). */
    public static long maxFileSizeUnpadded(long bodyLength, int extensionBytes) {
        return maxStreamBytes(bodyLength) - 2L * SIZE_FIELD_BYTES - extensionBytes;
    }
}
