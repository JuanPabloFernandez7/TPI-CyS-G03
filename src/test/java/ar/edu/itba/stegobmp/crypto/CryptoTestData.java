package ar.edu.itba.stegobmp.crypto;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

/** Datos y utilidades de prueba compartidas por las pruebas de la capa criptográfica. */
final class CryptoTestData {

    static final String ASCII_PASSWORD = "oculto";
    static final String NON_ASCII_PASSWORD = "contraseña€ñ";
    static final String LONG_PASSWORD = "p4ssw0rd-".repeat(12).substring(0, 100);

    private static final HexFormat HEX = HexFormat.of();

    private CryptoTestData() {
    }

    static List<CipherSpec> allSpecs() {
        List<CipherSpec> specs = new ArrayList<>();
        for (CryptoAlgorithm algorithm : CryptoAlgorithm.values()) {
            for (CryptoMode mode : CryptoMode.values()) {
                specs.add(new CipherSpec(algorithm, mode));
            }
        }
        return specs;
    }

    /** Mismo patrón que los fixtures de OpenSSL: {@code byte[i] = (i * 37 + 11) mod 256}. */
    static byte[] pattern(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i * 37 + 11);
        }
        return data;
    }

    static byte[] seeded(long seed, int length) {
        byte[] data = new byte[length];
        new Random(seed).nextBytes(data);
        return data;
    }

    static byte[] hex(String text) {
        return HEX.parseHex(text.replace(" ", ""));
    }

    static String hex(byte[] bytes) {
        return HEX.formatHex(bytes);
    }

    static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    /** Clave e IV deterministas con las longitudes que exige el {@code spec}. */
    static DerivedKey fixedKey(CipherSpec spec) throws CryptoException {
        return DerivedKey.of(seeded(1001, spec.algorithm().keyBytes()), seeded(2002, spec.ivBytes()));
    }
}
