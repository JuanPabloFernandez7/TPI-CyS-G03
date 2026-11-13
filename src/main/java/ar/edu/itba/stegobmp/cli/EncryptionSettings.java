package ar.edu.itba.stegobmp.cli;

import java.util.Objects;

public record EncryptionSettings(CipherAlgorithm algorithm, CipherMode mode, String password) {
    public static final CipherAlgorithm DEFAULT_ALGORITHM = CipherAlgorithm.AES128;
    public static final CipherMode DEFAULT_MODE = CipherMode.CBC;

    public EncryptionSettings {
        Objects.requireNonNull(algorithm);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(password);
    }

    @Override
    public String toString() {
        return "EncryptionSettings[algorithm=" + algorithm + ", mode=" + mode + ", password=***]";
    }
}
