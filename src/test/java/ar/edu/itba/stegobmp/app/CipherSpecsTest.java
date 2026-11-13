package ar.edu.itba.stegobmp.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.itba.stegobmp.cli.CipherAlgorithm;
import ar.edu.itba.stegobmp.cli.CipherMode;
import ar.edu.itba.stegobmp.cli.EncryptionSettings;
import ar.edu.itba.stegobmp.cli.StegAlgorithm;
import ar.edu.itba.stegobmp.crypto.CipherSpec;
import ar.edu.itba.stegobmp.crypto.CryptoAlgorithm;
import ar.edu.itba.stegobmp.crypto.CryptoMode;
import org.junit.jupiter.api.Test;

class CipherSpecsTest {

    @Test
    void mapsEveryCliCombination() {
        CryptoAlgorithm[] algorithms = {CryptoAlgorithm.AES_128, CryptoAlgorithm.AES_192, CryptoAlgorithm.AES_256,
                CryptoAlgorithm.TRIPLE_DES};
        CryptoMode[] modes = {CryptoMode.ECB, CryptoMode.CBC, CryptoMode.CFB8, CryptoMode.OFB};
        for (CipherAlgorithm a : CipherAlgorithm.values()) {
            for (CipherMode m : CipherMode.values()) {
                CipherSpec spec = CipherSpecs.from(new EncryptionSettings(a, m, "x"));
                assertEquals(algorithms[a.ordinal()], spec.algorithm());
                assertEquals(modes[m.ordinal()], spec.mode());
            }
        }
    }

    @Test
    void cliCfbIsEightBitCfb() {
        CipherSpec spec = CipherSpecs.from(new EncryptionSettings(CipherAlgorithm.AES128, CipherMode.CFB, "x"));
        assertEquals(CryptoMode.CFB8, spec.mode());
        assertTrue(spec.transformation().contains("CFB8"));
    }

    @Test
    void allCliAlgorithmsAreAvailable() {
        assertTrue(Steganographies.forAlgorithm(StegAlgorithm.LSB1).isPresent());
        assertTrue(Steganographies.forAlgorithm(StegAlgorithm.LSB4).isPresent());
        assertTrue(Steganographies.forAlgorithm(StegAlgorithm.LSBI).isPresent());
    }
}
