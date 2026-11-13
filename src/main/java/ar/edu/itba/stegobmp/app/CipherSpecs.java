package ar.edu.itba.stegobmp.app;

import ar.edu.itba.stegobmp.cli.CipherAlgorithm;
import ar.edu.itba.stegobmp.cli.CipherMode;
import ar.edu.itba.stegobmp.cli.EncryptionSettings;
import ar.edu.itba.stegobmp.crypto.CipherSpec;
import ar.edu.itba.stegobmp.crypto.CryptoAlgorithm;
import ar.edu.itba.stegobmp.crypto.CryptoMode;

/** Traduce la configuración de la CLI a la de la capa criptográfica. {@code cfb} de la CLI es CFB de 8 bits (A1). */
final class CipherSpecs {
    private CipherSpecs() {
    }

    static CipherSpec from(EncryptionSettings settings) {
        return new CipherSpec(algorithm(settings.algorithm()), mode(settings.mode()));
    }

    private static CryptoAlgorithm algorithm(CipherAlgorithm algorithm) {
        return switch (algorithm) {
            case AES128 -> CryptoAlgorithm.AES_128;
            case AES192 -> CryptoAlgorithm.AES_192;
            case AES256 -> CryptoAlgorithm.AES_256;
            case TRIPLE_DES -> CryptoAlgorithm.TRIPLE_DES;
        };
    }

    private static CryptoMode mode(CipherMode mode) {
        return switch (mode) {
            case ECB -> CryptoMode.ECB;
            case CBC -> CryptoMode.CBC;
            case CFB -> CryptoMode.CFB8;
            case OFB -> CryptoMode.OFB;
        };
    }
}
