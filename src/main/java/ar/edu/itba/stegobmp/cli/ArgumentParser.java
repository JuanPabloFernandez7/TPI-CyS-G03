package ar.edu.itba.stegobmp.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Convierte los argumentos de línea de comandos en un {@link CommandOptions} validado. */
public final class ArgumentParser {
    private static final Set<String> VALUE_OPTIONS = Set.of("-in", "-p", "-out", "-steg", "-a", "-m", "-pass");

    private ArgumentParser() {
    }

    public static boolean isHelpRequest(String[] args) {
        return args.length == 1 && ("-h".equals(args[0]) || "--help".equals(args[0]));
    }

    public static CommandOptions parse(String[] args) throws UsageException {
        Operation operation = null;
        Map<String, String> values = new LinkedHashMap<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-embed".equals(arg) || "-extract".equals(arg)) {
                Operation requested = "-embed".equals(arg) ? Operation.EMBED : Operation.EXTRACT;
                if (operation == requested) {
                    throw new UsageException("Opción duplicada: " + arg);
                }
                if (operation != null) {
                    throw new UsageException("-embed y -extract son excluyentes");
                }
                operation = requested;
            } else if (VALUE_OPTIONS.contains(arg)) {
                if (values.containsKey(arg)) {
                    throw new UsageException("Opción duplicada: " + arg);
                }
                if (i + 1 >= args.length) {
                    throw new UsageException("Falta el valor de la opción " + arg);
                }
                values.put(arg, args[++i]);
            } else {
                throw new UsageException("Argumento desconocido: " + arg);
            }
        }

        if (operation == null) {
            throw new UsageException("Falta indicar la operación: -embed o -extract");
        }
        requireOptions(operation, values);

        StegAlgorithm steg = StegAlgorithm.fromArgument(values.get("-steg"));
        if (steg == null) {
            throw new UsageException("Valor inválido para -steg: '" + values.get("-steg")
                    + "' (válidos: LSB1, LSB4, LSBI)");
        }

        return new CommandOptions(
                operation,
                operation == Operation.EMBED ? Path.of(values.get("-in")) : null,
                Path.of(values.get("-p")),
                Path.of(values.get("-out")),
                steg,
                parseEncryption(values));
    }

    private static void requireOptions(Operation operation, Map<String, String> values) throws UsageException {
        String flag = operation == Operation.EMBED ? "-embed" : "-extract";
        if (operation == Operation.EXTRACT && values.containsKey("-in")) {
            throw new UsageException("La opción -in no es válida con -extract");
        }
        List<String> required = operation == Operation.EMBED
                ? List.of("-in", "-p", "-out", "-steg")
                : List.of("-p", "-out", "-steg");
        List<String> missing = new ArrayList<>();
        for (String option : required) {
            if (!values.containsKey(option)) {
                missing.add(option);
            }
        }
        if (!missing.isEmpty()) {
            throw new UsageException("Faltan opciones obligatorias para " + flag + ": " + String.join(", ", missing));
        }
    }

    private static Optional<EncryptionSettings> parseEncryption(Map<String, String> values) throws UsageException {
        CipherAlgorithm algorithm = null;
        if (values.containsKey("-a")) {
            algorithm = CipherAlgorithm.fromArgument(values.get("-a"));
            if (algorithm == null) {
                throw new UsageException("Valor inválido para -a: '" + values.get("-a")
                        + "' (válidos: aes128, aes192, aes256, 3des)");
            }
        }
        CipherMode mode = null;
        if (values.containsKey("-m")) {
            mode = CipherMode.fromArgument(values.get("-m"));
            if (mode == null) {
                throw new UsageException("Valor inválido para -m: '" + values.get("-m")
                        + "' (válidos: ecb, cfb, ofb, cbc)");
            }
        }
        if (!values.containsKey("-pass")) {
            // La consigna: sin password no se cifra; solo se esteganografía.
            return Optional.empty();
        }
        return Optional.of(new EncryptionSettings(
                algorithm != null ? algorithm : EncryptionSettings.DEFAULT_ALGORITHM,
                mode != null ? mode : EncryptionSettings.DEFAULT_MODE,
                values.get("-pass")));
    }
}
