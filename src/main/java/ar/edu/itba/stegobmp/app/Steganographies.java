package ar.edu.itba.stegobmp.app;

import ar.edu.itba.stegobmp.cli.StegAlgorithm;
import ar.edu.itba.stegobmp.steganography.Lsb1;
import ar.edu.itba.stegobmp.steganography.Lsb4;
import ar.edu.itba.stegobmp.steganography.Lsbi;
import ar.edu.itba.stegobmp.steganography.Steganography;
import java.util.Optional;

/** Algoritmos de esteganografía disponibles. */
public final class Steganographies {
    private Steganographies() {
    }

    public static Optional<Steganography> forAlgorithm(StegAlgorithm algorithm) {
        return switch (algorithm) {
            case LSB1 -> Optional.of(new Lsb1());
            case LSB4 -> Optional.of(new Lsb4());
            case LSBI -> Optional.of(new Lsbi());
        };
    }
}
