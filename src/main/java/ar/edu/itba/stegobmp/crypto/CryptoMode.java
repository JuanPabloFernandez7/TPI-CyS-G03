package ar.edu.itba.stegobmp.crypto;

/**
 * Modos de operación. Único punto donde se decide el feedback: CFB es de 8 bits (A1) y OFB de bloque completo (A2).
 * Cambiar {@link #CFB8} por CFB de bloque completo implicaría solo cambiar {@code jcaMode}.
 */
public enum CryptoMode {
    ECB("ECB", "PKCS5Padding", false),
    CBC("CBC", "PKCS5Padding", true),
    CFB8("CFB8", "NoPadding", true),
    OFB("OFB", "NoPadding", true);

    private final String jcaMode;
    private final String padding;
    private final boolean usesIv;

    CryptoMode(String jcaMode, String padding, boolean usesIv) {
        this.jcaMode = jcaMode;
        this.padding = padding;
        this.usesIv = usesIv;
    }

    public String jcaMode() {
        return jcaMode;
    }

    public String padding() {
        return padding;
    }

    public boolean usesIv() {
        return usesIv;
    }

    public boolean isPadded() {
        return !"NoPadding".equals(padding);
    }
}
