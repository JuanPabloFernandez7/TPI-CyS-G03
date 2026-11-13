package ar.edu.itba.stegobmp.cli;

public enum CipherMode {
    ECB("ecb"),
    CBC("cbc"),
    CFB("cfb"),
    OFB("ofb");

    private final String argument;

    CipherMode(String argument) {
        this.argument = argument;
    }

    public String argument() {
        return argument;
    }

    static CipherMode fromArgument(String value) {
        for (CipherMode mode : values()) {
            if (mode.argument.equals(value)) {
                return mode;
            }
        }
        return null;
    }
}
