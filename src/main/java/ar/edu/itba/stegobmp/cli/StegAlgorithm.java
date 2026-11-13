package ar.edu.itba.stegobmp.cli;

public enum StegAlgorithm {
    LSB1,
    LSB4,
    LSBI;

    static StegAlgorithm fromArgument(String value) {
        for (StegAlgorithm algorithm : values()) {
            if (algorithm.name().equals(value)) {
                return algorithm;
            }
        }
        return null;
    }
}
