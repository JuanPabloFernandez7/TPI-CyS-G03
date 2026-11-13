package ar.edu.itba.stegobmp.crypto;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Envoltorio estricto sobre {@code openssl enc}. Reconoce exactamente los dos formatos de salida conocidos
 * (sin encabezado, como OpenSSL 3.6.2, o {@code Salted__} + sal, como LibreSSL 3.3.6) y falla ante cualquier otra cosa.
 * Nunca usa {@code -nosalt}.
 */
final class OpenSslHarness {

    static final String ZERO_SALT = "0000000000000000";
    private static final byte[] MAGIC = "Salted__".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern VERSION = Pattern.compile("^(OpenSSL|LibreSSL) (\\d+\\.\\d+\\.\\d+)[^\\n]*$");

    enum Dialect {
        NO_HEADER, SALTED_HEADER
    }

    private final String executable;
    private final String version;
    private final Dialect dialect;

    private OpenSslHarness(String executable, String version, Dialect dialect) {
        this.executable = executable;
        this.version = version;
        this.dialect = dialect;
    }

    /** Vacío solo si no hay ejecutable; si existe pero se comporta de forma inesperada, falla. */
    static Optional<OpenSslHarness> detect() {
        String executable = System.getProperty("stegobmp.openssl", "openssl");
        String version;
        try {
            byte[] out = run(List.of(executable, "version"), null);
            version = new String(out, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            return Optional.empty();
        }
        if (!VERSION.matcher(version).matches()) {
            return fail("Salida inesperada de '" + executable + " version': " + version);
        }
        OpenSslHarness probe = new OpenSslHarness(executable, version, Dialect.NO_HEADER);
        byte[] raw = probe.rawEncrypt("aes-128-cbc", "probe".getBytes(StandardCharsets.UTF_8), new byte[23], ZERO_SALT);
        Dialect dialect = classify(raw, 32, ZERO_SALT, version);
        return Optional.of(new OpenSslHarness(executable, version, dialect));
    }

    String version() {
        return version;
    }

    Dialect dialect() {
        return dialect;
    }

    static String cipherName(CipherSpec spec) {
        String base = switch (spec.algorithm()) {
            case AES_128 -> "aes-128";
            case AES_192 -> "aes-192";
            case AES_256 -> "aes-256";
            case TRIPLE_DES -> "des-ede3";
        };
        if (spec.algorithm() == CryptoAlgorithm.TRIPLE_DES && spec.mode() == CryptoMode.ECB) {
            return base;
        }
        return base + "-" + spec.mode().name().toLowerCase();
    }

    /**
     * Distingue los dos formatos esperados por longitud y contenido. Cualquier otra cosa es un error.
     */
    static Dialect classify(byte[] output, long expectedCiphertextLength, String saltHex, String version) {
        byte[] salt = hex(saltHex);
        if (output.length == expectedCiphertextLength) {
            if (startsWithMagic(output)) {
                return fail(unexpected(version, output, "salida sin encabezado que empieza con Salted__"));
            }
            return Dialect.NO_HEADER;
        }
        if (output.length == expectedCiphertextLength + 16) {
            if (!startsWithMagic(output) || !Arrays.equals(Arrays.copyOfRange(output, 8, 16), salt)) {
                return fail(unexpected(version, output, "encabezado distinto de Salted__ + sal esperada"));
            }
            return Dialect.SALTED_HEADER;
        }
        return fail(unexpected(version, output, "longitud inesperada (esperada " + expectedCiphertextLength + " o +16)"));
    }

    static byte[] stripHeader(byte[] output, long expectedCiphertextLength, String saltHex, String version) {
        Dialect kind = classify(output, expectedCiphertextLength, saltHex, version);
        return kind == Dialect.NO_HEADER ? output : Arrays.copyOfRange(output, 16, output.length);
    }

    private static boolean startsWithMagic(byte[] bytes) {
        return bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), MAGIC);
    }

    private static String unexpected(String version, byte[] output, String why) {
        byte[] head = Arrays.copyOf(output, Math.min(24, output.length));
        return "Formato inesperado de openssl enc (" + version + "): " + why + "; longitud=" + output.length
                + ", primeros bytes=" + CryptoTestData.hex(head);
    }

    byte[] encrypt(CipherSpec spec, String password, byte[] data) {
        return encryptNamed(cipherName(spec), password, data, ZERO_SALT, spec.ciphertextLength(data.length));
    }

    byte[] encryptNamed(String cipher, String password, byte[] data, String saltHex, long expectedLength) {
        byte[] raw = rawEncrypt(cipher, password.getBytes(StandardCharsets.UTF_8), data, saltHex);
        return stripHeader(raw, expectedLength, saltHex, version);
    }

    /** Descifra anteponiendo {@code Salted__} + 8 ceros y sin {@code -S}: es válido en ambos dialectos. */
    byte[] decrypt(CipherSpec spec, String password, byte[] ciphertext) {
        return withTemp(dir -> {
            byte[] input = CryptoTestData.concat(MAGIC, new byte[8], ciphertext);
            Path in = write(dir, "in", input);
            Path out = dir.resolve("out");
            List<String> cmd = baseCommand("enc", "-d", "-" + cipherName(spec), "-pbkdf2", "-iter", "10000", "-md", "sha256");
            cmd.addAll(passwordArgs(dir, password.getBytes(StandardCharsets.UTF_8)));
            cmd.addAll(List.of("-in", in.toString(), "-out", out.toString()));
            run(cmd, null);
            return Files.readAllBytes(out);
        });
    }

    /** Clave e IV según {@code -P}, interpretados de forma estricta. */
    byte[][] derive(CipherSpec spec, String password) {
        return withTemp(dir -> {
            List<String> cmd = baseCommand("enc", "-" + cipherName(spec), "-pbkdf2", "-iter", "10000", "-md", "sha256", "-S", ZERO_SALT, "-P");
            cmd.addAll(passwordArgs(dir, password.getBytes(StandardCharsets.UTF_8)));
            String text = new String(run(cmd, null), StandardCharsets.UTF_8);
            byte[] key = null;
            byte[] iv = new byte[0];
            boolean salt = false;
            for (String line : text.split("\n")) {
                line = line.strip();
                if (line.isEmpty()) {
                    continue;
                }
                Matcher m = Pattern.compile("^(salt|key|iv ?)=([0-9A-F]+)$").matcher(line);
                if (!m.matches()) {
                    fail("Línea inesperada en la salida de -P (" + version + "): " + line);
                }
                switch (m.group(1).strip()) {
                    case "salt" -> {
                        if (!m.group(2).equals(ZERO_SALT)) {
                            fail("Sal inesperada en -P: " + line);
                        }
                        salt = true;
                    }
                    case "key" -> key = hex(m.group(2));
                    default -> iv = hex(m.group(2));
                }
            }
            if (!salt || key == null) {
                fail("La salida de -P no contiene salt y key: " + text);
            }
            return new byte[][] {key, iv};
        });
    }

    /** Cifra con clave e IV explícitos (-K/-iv), sin derivación ni encabezado. */
    byte[] encryptWithKey(CipherSpec spec, byte[] key, byte[] iv, byte[] data) {
        return withTemp(dir -> {
            Path in = write(dir, "in", data);
            Path out = dir.resolve("out");
            List<String> cmd = baseCommand("enc", "-" + cipherName(spec), "-K", CryptoTestData.hex(key));
            if (spec.mode().usesIv()) {
                cmd.addAll(List.of("-iv", CryptoTestData.hex(iv)));
            }
            cmd.addAll(List.of("-in", in.toString(), "-out", out.toString()));
            run(cmd, null);
            byte[] result = Files.readAllBytes(out);
            if (startsWithMagic(result) && result.length == spec.ciphertextLength(data.length) + 16) {
                fail("openssl enc -K escribió un encabezado Salted__ inesperado (" + version + ")");
            }
            return result;
        });
    }

    private byte[] rawEncrypt(String cipher, byte[] password, byte[] data, String saltHex) {
        return withTemp(dir -> {
            Path in = write(dir, "in", data);
            Path out = dir.resolve("out");
            List<String> cmd = baseCommand("enc", "-" + cipher, "-pbkdf2", "-iter", "10000", "-md", "sha256", "-S", saltHex);
            cmd.addAll(passwordArgs(dir, password));
            cmd.addAll(List.of("-in", in.toString(), "-out", out.toString()));
            run(cmd, null);
            return Files.readAllBytes(out);
        });
    }

    private List<String> baseCommand(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable);
        cmd.addAll(Arrays.asList(args));
        return cmd;
    }

    // La contraseña viaja por archivo (bytes UTF-8 exactos) para no depender de la codificación de argv.
    private static List<String> passwordArgs(Path dir, byte[] password) throws IOException {
        if (password.length == 0) {
            return List.of("-pass", "pass:");
        }
        Path file = write(dir, "pw", password);
        return List.of("-pass", "file:" + file);
    }

    private static Path write(Path dir, String name, byte[] data) throws IOException {
        Path path = dir.resolve(name);
        Files.write(path, data);
        return path;
    }

    interface Job<T> {
        T run(Path dir) throws IOException;
    }

    private static <T> T withTemp(Job<T> job) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("stegobmp-openssl");
            return job.run(dir);
        } catch (IOException e) {
            return fail("Fallo al ejecutar openssl: " + e.getMessage());
        } finally {
            if (dir != null) {
                File[] files = dir.toFile().listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
                dir.toFile().delete();
            }
        }
    }

    private static byte[] run(List<String> command, File stdin) throws IOException {
        Path outFile = Files.createTempFile("stegobmp-openssl-out", ".txt");
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            builder.redirectOutput(outFile.toFile());
            builder.redirectInput(stdin != null ? stdin : new File("/dev/null"));
            Process process = builder.start();
            try {
                if (!process.waitFor(60, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return fail("Timeout ejecutando " + command.get(0));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                return fail("Interrumpido");
            }
            byte[] output = Files.readAllBytes(outFile);
            if (process.exitValue() != 0) {
                return fail("openssl terminó con código " + process.exitValue() + ": "
                        + new String(output, StandardCharsets.UTF_8));
            }
            return output;
        } finally {
            Files.deleteIfExists(outFile);
        }
    }

    private static byte[] hex(String text) {
        return CryptoTestData.hex(text.toLowerCase());
    }
}
