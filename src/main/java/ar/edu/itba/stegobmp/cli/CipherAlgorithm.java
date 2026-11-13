package ar.edu.itba.stegobmp.cli;

public enum CipherAlgorithm {
    AES128("aes128"),
    AES192("aes192"),
    AES256("aes256"),
    TRIPLE_DES("3des");

    private final String argument;

    CipherAlgorithm(String argument) {
        this.argument = argument;
    }

    public String argument() {
        return argument;
    }

    static CipherAlgorithm fromArgument(String value) {
        for (CipherAlgorithm algorithm : values()) {
            if (algorithm.argument.equals(value)) {
                return algorithm;
            }
        }
        return null;
    }
}
