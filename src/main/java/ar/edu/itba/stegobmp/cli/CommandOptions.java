package ar.edu.itba.stegobmp.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuración validada de una ejecución. {@code input} solo existe en EMBED;
 * {@code encryption} está vacío cuando no se indicó password.
 */
public record CommandOptions(
        Operation operation,
        Path input,
        Path carrier,
        Path output,
        StegAlgorithm steg,
        Optional<EncryptionSettings> encryption) {

    public CommandOptions {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(carrier);
        Objects.requireNonNull(output);
        Objects.requireNonNull(steg);
        Objects.requireNonNull(encryption);
        if ((operation == Operation.EMBED) != (input != null)) {
            throw new IllegalArgumentException("-in corresponde únicamente a EMBED");
        }
    }
}
