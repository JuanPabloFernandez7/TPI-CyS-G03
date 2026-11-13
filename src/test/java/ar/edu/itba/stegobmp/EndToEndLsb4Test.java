package ar.edu.itba.stegobmp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.itba.stegobmp.bmp.BmpTestData;
import ar.edu.itba.stegobmp.cli.CipherAlgorithm;
import ar.edu.itba.stegobmp.cli.CipherMode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EndToEndLsb4Test {
    @TempDir
    Path dir;

    private String lastError = "";

    private int run(String... args) {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = Main.run(args, new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        lastError = err.toString(StandardCharsets.UTF_8);
        return exit;
    }

    private Path carrier(String name, int width, int height) throws Exception {
        Path path = dir.resolve(name);
        Files.write(path, BmpTestData.valid(width, height).build());
        return path;
    }

    private Path input(String name, byte[] bytes) throws Exception {
        Path path = dir.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private int embed(Path input, Path bmp, Path output, String... extra) {
        List<String> args = new ArrayList<>(List.of("-embed", "-in", input.toString(), "-p", bmp.toString(),
                "-out", output.toString(), "-steg", "LSB4"));
        args.addAll(Arrays.asList(extra));
        return run(args.toArray(String[]::new));
    }

    private int extract(Path bmp, Path output, String... extra) {
        List<String> args = new ArrayList<>(List.of("-extract", "-p", bmp.toString(),
                "-out", output.toString(), "-steg", "LSB4"));
        args.addAll(Arrays.asList(extra));
        return run(args.toArray(String[]::new));
    }

    private static byte[] seeded(int length, long seed) {
        byte[] bytes = new byte[length];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    @Test
    void plainRoundTripPreservesAllFileBytesAndExtension() throws Exception {
        byte[] content = seeded(1200, 0x4C534234L);
        Path source = input("archivo.bin", content);
        Path carrier = carrier("portador.bmp", 32, 32);
        byte[] originalCarrier = Files.readAllBytes(carrier);
        Path stego = dir.resolve("resultado.bmp");
        Path outputBase = dir.resolve("recuperado");

        assertEquals(Main.EXIT_OK, embed(source, carrier, stego), lastError);
        assertEquals(Main.EXIT_OK, extract(stego, outputBase), lastError);
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("recuperado.bin")));
        assertArrayEquals(originalCarrier, Files.readAllBytes(carrier));
    }

    @Test
    void supportsEmptyAndExtensionlessFiles() throws Exception {
        Path bmp = carrier("portador.bmp", 12, 12);
        Path empty = input("vacio.dat", new byte[0]);
        Path emptyStego = dir.resolve("vacio.bmp");
        assertEquals(Main.EXIT_OK, embed(empty, bmp, emptyStego), lastError);
        assertEquals(Main.EXIT_OK, extract(emptyStego, dir.resolve("vacio_rec")), lastError);
        assertArrayEquals(new byte[0], Files.readAllBytes(dir.resolve("vacio_rec.dat")));

        byte[] content = seeded(31, 31);
        Path noExtension = input("sin_extension", content);
        Path noExtensionStego = dir.resolve("sin_extension.bmp");
        assertEquals(Main.EXIT_OK, embed(noExtension, bmp, noExtensionStego), lastError);
        assertEquals(Main.EXIT_OK, extract(noExtensionStego, dir.resolve("salida")), lastError);
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("salida")));
    }

    @Test
    void roundTripsAllSixteenCipherSpecifications() throws Exception {
        byte[] content = seeded(133, 16);
        Path source = input("cifrado.dat", content);
        Path bmp = carrier("portador.bmp", 64, 64);

        for (CipherAlgorithm algorithm : CipherAlgorithm.values()) {
            for (CipherMode mode : CipherMode.values()) {
                Path stego = dir.resolve(algorithm + "_" + mode + ".bmp");
                Path output = dir.resolve(algorithm + "_" + mode + "_out");
                String[] crypto = {"-a", algorithmArgument(algorithm), "-m", modeArgument(mode), "-pass", "password"};
                assertEquals(Main.EXIT_OK, embed(source, bmp, stego, crypto),
                        algorithm + "/" + mode + ": " + lastError);
                assertEquals(Main.EXIT_OK, extract(stego, output, crypto),
                        algorithm + "/" + mode + ": " + lastError);
                assertArrayEquals(content, Files.readAllBytes(Path.of(output + ".dat")),
                        algorithm + "/" + mode);
            }
        }
    }

    @Test
    void appliesPasswordOnlyDefaults() throws Exception {
        byte[] content = seeded(80, 19);
        Path source = input("default.txt", content);
        Path bmp = carrier("portador.bmp", 32, 32);
        Path stego = dir.resolve("defaults.bmp");
        Path output = dir.resolve("default_out");

        assertEquals(Main.EXIT_OK, embed(source, bmp, stego, "-pass", "pw"), lastError);
        assertEquals(Main.EXIT_OK, extract(stego, output, "-pass", "pw"), lastError);
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("default_out.txt")));
    }

    @Test
    void checksExactCapacityAndDoesNotWriteWhenInsufficient() throws Exception {
        Path source = input("x.txt", new byte[] {0x42});
        assertEquals(Main.EXIT_OK, embed(source, carrier("exact.bmp", 1, 5), dir.resolve("exact_out.bmp")),
                lastError);

        Path tooSmall = carrier("small.bmp", 1, 4);
        Path output = dir.resolve("must_not_exist.bmp");
        assertEquals(Main.EXIT_FAILURE, embed(source, tooSmall, output));
        assertTrue(lastError.contains("capacidad suficiente"), lastError);
        assertFalse(Files.exists(output));
    }

    @Test
    void preservesBmpGapAndTrailingBytesAndOnlyChangesUsedLowNibbles() throws Exception {
        byte[] original = BmpTestData.valid(7, 12).gap(9).trailing(7).build();
        Path bmp = dir.resolve("with_extra.bmp");
        Files.write(bmp, original);
        Path source = input("small.txt", seeded(20, 7));
        Path stego = dir.resolve("with_extra_stego.bmp");

        assertEquals(Main.EXIT_OK, embed(source, bmp, stego), lastError);
        byte[] result = Files.readAllBytes(stego);
        int bodyStart = 54 + 9;
        int bodyLength = 24 * 12;
        int bodyEnd = bodyStart + bodyLength;
        assertArrayEquals(Arrays.copyOfRange(original, 0, bodyStart),
                Arrays.copyOfRange(result, 0, bodyStart));
        assertArrayEquals(Arrays.copyOfRange(original, bodyEnd, original.length),
                Arrays.copyOfRange(result, bodyEnd, result.length));
        int hiddenBytes = 4 + 20 + 6;
        for (int i = 0; i < bodyLength; i++) {
            int difference = (original[bodyStart + i] ^ result[bodyStart + i]) & 0xFF;
            if (i < hiddenBytes * 2) {
                assertEquals(0, difference & 0xF0, "upper nibble changed at body byte " + i);
            } else {
                assertEquals(0, difference, "unused body byte changed at " + i);
            }
        }
    }

    @Test
    void malformedCarrierAndPayloadAreReported() throws Exception {
        Path source = input("x.txt", new byte[] {1});
        Path invalidBmp = input("not.bmp", new byte[] {1, 2, 3});
        assertEquals(Main.EXIT_FAILURE, embed(source, invalidBmp, dir.resolve("invalid_out.bmp")));
        assertTrue(lastError.contains("BMP válido"), lastError);

        Path plainCarrier = carrier("plain.bmp", 16, 16);
        Path invalidPayload = dir.resolve("invalid_payload.bmp");
        assertEquals(Main.EXIT_OK, embed(source, plainCarrier, invalidPayload), lastError);
        Path output = dir.resolve("invalid_payload_out");
        assertEquals(Main.EXIT_FAILURE,
                extract(invalidPayload, output, "-a", "aes128", "-m", "cbc", "-pass", "wrong"));
        assertFalse(Files.exists(output));
    }

    private static String algorithmArgument(CipherAlgorithm algorithm) {
        return switch (algorithm) {
            case AES128 -> "aes128";
            case AES192 -> "aes192";
            case AES256 -> "aes256";
            case TRIPLE_DES -> "3des";
        };
    }

    private static String modeArgument(CipherMode mode) {
        return switch (mode) {
            case ECB -> "ecb";
            case CBC -> "cbc";
            case CFB -> "cfb";
            case OFB -> "ofb";
        };
    }
}
