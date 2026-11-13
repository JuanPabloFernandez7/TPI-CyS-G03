package ar.edu.itba.stegobmp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.itba.stegobmp.bmp.BmpTestData;
import ar.edu.itba.stegobmp.steganography.Lsb1;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Flujo completo EMBED/EXTRACT con LSB1 a través de {@link Main#run}, con archivos reales en un directorio temporal. */
class EndToEndLsb1Test {

    @TempDir
    Path dir;

    private String lastErr = "";

    private int main(String... args) {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(args, new PrintStream(new ByteArrayOutputStream()), new PrintStream(err, true, StandardCharsets.UTF_8));
        lastErr = err.toString(StandardCharsets.UTF_8);
        return code;
    }

    private Path carrier(int width, int height) throws IOException {
        Path p = dir.resolve("portador.bmp");
        Files.write(p, BmpTestData.valid(width, height).build());
        return p;
    }

    private Path file(String name, byte[] content) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, content);
        return p;
    }

    private int embed(Path in, Path bmp, Path out, String... extra) {
        List<String> args = new ArrayList<>(List.of("-embed", "-in", in.toString(), "-p", bmp.toString(),
                "-out", out.toString(), "-steg", "LSB1"));
        args.addAll(Arrays.asList(extra));
        return main(args.toArray(String[]::new));
    }

    private int extract(Path bmp, Path out, String... extra) {
        List<String> args = new ArrayList<>(List.of("-extract", "-p", bmp.toString(), "-out", out.toString(), "-steg", "LSB1"));
        args.addAll(Arrays.asList(extra));
        return main(args.toArray(String[]::new));
    }

    private static byte[] allByteValues(int length, long seed) {
        byte[] data = new byte[length];
        new Random(seed).nextBytes(data);
        for (int i = 0; i < Math.min(256, length); i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    // ---- sin cifrado ----

    @Test
    void plainRoundTripRecoversTheFileByteForByte() throws Exception {
        byte[] content = allByteValues(1000, 1);
        Path in = file("archivo.txt", content);
        Path bmp = carrier(64, 50);
        Path stego = dir.resolve("resultado.bmp");

        assertEquals(0, embed(in, bmp, stego), lastErr);
        assertEquals(0, extract(stego, dir.resolve("recuperado")), lastErr);
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("recuperado.txt")));
    }

    @ParameterizedTest
    @MethodSource("extensions")
    void extensionsAreRestoredAndNoExtensionKeepsTheOutName(String name, String expectedSuffix) throws Exception {
        byte[] content = allByteValues(300, 2);
        Path in = file(name, content);
        Path stego = dir.resolve("r.bmp");
        assertEquals(0, embed(in, carrier(40, 40), stego), lastErr);
        assertEquals(0, extract(stego, dir.resolve("salida")), lastErr);
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("salida" + expectedSuffix)));
    }

    static Stream<Arguments> extensions() {
        return Stream.of(Arguments.of("a.txt", ".txt"), Arguments.of("a.png", ".png"), Arguments.of("a.tar.gz", ".gz"),
                Arguments.of("sin_extension", ""), Arguments.of("termina_en_punto.", ""),
                Arguments.of("largo.extensionmuylarga", ".extensionmuylarga"), Arguments.of("x.c", ".c"));
    }

    @Test
    void emptyFileRoundTrips() throws Exception {
        Path in = file("vacio.dat", new byte[0]);
        Path stego = dir.resolve("r.bmp");
        assertEquals(0, embed(in, carrier(8, 8), stego), lastErr);
        assertEquals(0, extract(stego, dir.resolve("out")), lastErr);
        assertEquals(0, Files.size(dir.resolve("out.dat")));
    }

    @Test
    void embeddingNeverModifiesTheCarrierOrTheInput() throws Exception {
        Path in = file("a.txt", allByteValues(50, 3));
        Path bmp = carrier(30, 30);
        byte[] bmpBefore = Files.readAllBytes(bmp);
        byte[] inBefore = Files.readAllBytes(in);
        assertEquals(0, embed(in, bmp, dir.resolve("r.bmp")), lastErr);
        assertArrayEquals(bmpBefore, Files.readAllBytes(bmp));
        assertArrayEquals(inBefore, Files.readAllBytes(in));
    }

    // ---- con cifrado ----

    static Stream<Arguments> allCipherCombinations() {
        List<Arguments> list = new ArrayList<>();
        for (String a : new String[] {"aes128", "aes192", "aes256", "3des"}) {
            for (String m : new String[] {"ecb", "cbc", "cfb", "ofb"}) {
                list.add(Arguments.of(a, m));
            }
        }
        return list.stream();
    }

    @ParameterizedTest(name = "{0}-{1}")
    @MethodSource("allCipherCombinations")
    void encryptedRoundTripForEveryAlgorithmAndMode(String algorithm, String mode) throws Exception {
        byte[] content = allByteValues(414, 4);
        Path in = file("archivo.txt", content);
        Path stego = dir.resolve("r.bmp");
        assertEquals(0, embed(in, carrier(60, 40), stego, "-a", algorithm, "-m", mode, "-pass", "oculto"), lastErr);
        assertEquals(0, extract(stego, dir.resolve("rec"), "-a", algorithm, "-m", mode, "-pass", "oculto"), lastErr);
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("rec.txt")));
    }

    @Test
    void encryptedImageDiffersFromPlainOneAndDoesNotRevealTheContent() throws Exception {
        Path in = file("a.txt", "texto secreto".getBytes(StandardCharsets.UTF_8));
        Path bmp = carrier(30, 30);
        assertEquals(0, embed(in, bmp, dir.resolve("plain.bmp")), lastErr);
        assertEquals(0, embed(in, bmp, dir.resolve("enc.bmp"), "-pass", "k"), lastErr);
        assertFalse(Arrays.equals(Files.readAllBytes(dir.resolve("plain.bmp")), Files.readAllBytes(dir.resolve("enc.bmp"))));
    }

    @Test
    void passwordAloneDefaultsToAes128Cbc() throws Exception {
        byte[] content = allByteValues(200, 5);
        Path in = file("a.bin", content);
        Path bmp = carrier(40, 40);
        assertEquals(0, embed(in, bmp, dir.resolve("a.bmp"), "-pass", "pw"), lastErr);
        assertEquals(0, embed(in, bmp, dir.resolve("b.bmp"), "-a", "aes128", "-m", "cbc", "-pass", "pw"), lastErr);
        assertArrayEquals(Files.readAllBytes(dir.resolve("a.bmp")), Files.readAllBytes(dir.resolve("b.bmp")));
        assertEquals(0, extract(dir.resolve("a.bmp"), dir.resolve("o"), "-pass", "pw"), lastErr);
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("o.bin")));
    }

    @Test
    void algorithmAndPasswordDefaultToCbc() throws Exception {
        Path in = file("a.bin", allByteValues(100, 6));
        Path bmp = carrier(40, 40);
        assertEquals(0, embed(in, bmp, dir.resolve("a.bmp"), "-a", "3des", "-pass", "pw"), lastErr);
        assertEquals(0, embed(in, bmp, dir.resolve("b.bmp"), "-a", "3des", "-m", "cbc", "-pass", "pw"), lastErr);
        assertArrayEquals(Files.readAllBytes(dir.resolve("a.bmp")), Files.readAllBytes(dir.resolve("b.bmp")));
    }

    @Test
    void modeAndPasswordDefaultToAes128() throws Exception {
        Path in = file("a.bin", allByteValues(100, 7));
        Path bmp = carrier(40, 40);
        assertEquals(0, embed(in, bmp, dir.resolve("a.bmp"), "-m", "ofb", "-pass", "pw"), lastErr);
        assertEquals(0, embed(in, bmp, dir.resolve("b.bmp"), "-a", "aes128", "-m", "ofb", "-pass", "pw"), lastErr);
        assertArrayEquals(Files.readAllBytes(dir.resolve("a.bmp")), Files.readAllBytes(dir.resolve("b.bmp")));
    }

    @Test
    void algorithmAndModeWithoutPasswordDoNotEncrypt() throws Exception {
        byte[] content = allByteValues(100, 8);
        Path in = file("a.bin", content);
        Path bmp = carrier(40, 40);
        assertEquals(0, embed(in, bmp, dir.resolve("a.bmp"), "-a", "aes256", "-m", "ecb"), lastErr);
        assertEquals(0, embed(in, bmp, dir.resolve("b.bmp")), lastErr);
        assertArrayEquals(Files.readAllBytes(dir.resolve("a.bmp")), Files.readAllBytes(dir.resolve("b.bmp")));
        assertEquals(0, extract(dir.resolve("a.bmp"), dir.resolve("o")), lastErr);
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("o.bin")));
    }

    @Test
    void nonAsciiAndDashLeadingPasswordsWork() throws Exception {
        byte[] content = allByteValues(120, 9);
        Path in = file("a.txt", content);
        for (String password : new String[] {"contraseña€ñ", "-pass", "-a"}) {
            Path stego = dir.resolve("r.bmp");
            assertEquals(0, embed(in, carrier(40, 40), stego, "-pass", password), lastErr);
            assertEquals(0, extract(stego, dir.resolve("o"), "-pass", password), lastErr);
            assertArrayEquals(content, Files.readAllBytes(dir.resolve("o.txt")));
        }
    }

    // ---- capacidad ----

    // Un BMP de ancho 8 tiene 24 bytes por fila sin padding: capacidad LSB1 = 3 * alto bytes.

    @Test
    void plainExactCapacitySucceedsAndOneByteLessFails() throws Exception {
        byte[] content = allByteValues(21, 10); // 4 + 21 + 5 (".txt\0") = 30 bytes = capacidad de alto 10
        Path in = file("a.txt", content);
        Path stego = dir.resolve("r.bmp");
        assertEquals(0, embed(in, carrier(8, 10), stego), lastErr);
        assertEquals(0, extract(stego, dir.resolve("o")), lastErr);
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("o.txt")));

        Path failed = dir.resolve("fail.bmp");
        assertEquals(Main.EXIT_FAILURE, embed(in, carrier(8, 9), failed), lastErr);
        assertTrue(lastErr.contains("se necesitan 30 bytes") && lastErr.contains("capacidad máxima es 27"), lastErr);
        assertFalse(Files.exists(failed), "no debe escribirse el BMP si no hay capacidad");
    }

    @Test
    void paddedAndStreamModesNeedDifferentCapacity() throws Exception {
        // CBC con 21 bytes: plano 30 -> ciphertext 32 -> marco 36 = capacidad de alto 12
        Path cbcIn = file("a.txt", allByteValues(21, 11));
        assertEquals(0, embed(cbcIn, carrier(8, 12), dir.resolve("ok.bmp"), "-pass", "p"), lastErr);
        assertEquals(Main.EXIT_FAILURE, embed(cbcIn, carrier(8, 11), dir.resolve("no.bmp"), "-pass", "p"));
        assertTrue(lastErr.contains("se necesitan 36 bytes") && lastErr.contains("capacidad máxima es 33"), lastErr);

        // CFB8 con 23 bytes: plano 32 -> sin padding -> marco 36 = capacidad de alto 12
        Path cfbIn = file("b.txt", allByteValues(23, 12));
        assertEquals(0, embed(cfbIn, carrier(8, 12), dir.resolve("ok2.bmp"), "-m", "cfb", "-pass", "p"), lastErr);
        assertEquals(Main.EXIT_FAILURE, embed(cfbIn, carrier(8, 11), dir.resolve("no2.bmp"), "-m", "cfb", "-pass", "p"));
        // el mismo archivo con CBC necesita 52 bytes (padding)
        assertEquals(Main.EXIT_FAILURE, embed(cfbIn, carrier(8, 12), dir.resolve("no3.bmp"), "-pass", "p"));
        assertTrue(lastErr.contains("se necesitan 52 bytes"), lastErr);
        assertFalse(Files.exists(dir.resolve("no3.bmp")));
    }

    @Test
    void tripleDesPaddingUsesEightByteBlocks() throws Exception {
        // plano 4+21+5 = 30 -> 3DES-CBC ciphertext 32 -> marco 36
        Path in = file("a.txt", allByteValues(21, 13));
        assertEquals(Main.EXIT_FAILURE, embed(in, carrier(8, 11), dir.resolve("no.bmp"), "-a", "3des", "-pass", "p"));
        assertTrue(lastErr.contains("se necesitan 36 bytes"), lastErr);
        assertEquals(0, embed(in, carrier(8, 12), dir.resolve("ok.bmp"), "-a", "3des", "-pass", "p"), lastErr);
    }

    // ---- BMP con padding, gap y trailing ----

    @Test
    void gapPaddingAndTrailingBytesArePreservedAndOnlyLsbsChange() throws Exception {
        for (boolean encrypted : new boolean[] {false, true}) {
            byte[] bmpBytes = BmpTestData.valid(7, 40).gap(10).trailing(5).build(); // filas de 21 + 3 bytes de padding
            Path bmp = dir.resolve("p.bmp");
            Files.write(bmp, bmpBytes);
            byte[] content = allByteValues(100, 14);
            Path in = file("a.txt", content);
            Path stego = dir.resolve("r.bmp");
            String[] extra = encrypted ? new String[] {"-m", "ofb", "-pass", "pw"} : new String[0];
            assertEquals(0, embed(in, bmp, stego, extra), lastErr);

            byte[] result = Files.readAllBytes(stego);
            assertEquals(bmpBytes.length, result.length);
            int bodyStart = 54 + 10;
            int bodyEnd = bodyStart + 24 * 40;
            assertArrayEquals(Arrays.copyOfRange(bmpBytes, 0, bodyStart), Arrays.copyOfRange(result, 0, bodyStart));
            assertArrayEquals(Arrays.copyOfRange(bmpBytes, bodyEnd, bmpBytes.length), Arrays.copyOfRange(result, bodyEnd, result.length));
            int hidden = encrypted ? 4 + 4 + 100 + 5 : 4 + 100 + 5;
            for (int i = bodyStart; i < bodyEnd; i++) {
                int diff = (bmpBytes[i] ^ result[i]) & 0xFF;
                assertTrue((diff & 0xFE) == 0, "solo puede cambiar el bit menos significativo, byte " + i);
                if (i - bodyStart >= hidden * 8) {
                    assertEquals(0, diff, "los bytes posteriores al mensaje no se tocan, byte " + i);
                }
            }
            assertEquals(0, extract(stego, dir.resolve("o"), extra), lastErr);
            assertArrayEquals(content, Files.readAllBytes(dir.resolve("o.txt")));
        }
    }

    // ---- errores ----

    @Test
    void missingFilesAndInvalidImagesAreReportedWithoutWritingOutput() throws Exception {
        Path in = file("a.txt", new byte[10]);
        Path bmp = carrier(20, 20);
        Path out = dir.resolve("r.bmp");

        assertEquals(Main.EXIT_FAILURE, embed(dir.resolve("noexiste.txt"), bmp, out));
        assertTrue(lastErr.contains("no existe"), lastErr);
        assertEquals(Main.EXIT_FAILURE, embed(in, dir.resolve("noexiste.bmp"), out));
        assertTrue(lastErr.contains("no existe"), lastErr);
        assertEquals(Main.EXIT_FAILURE, extract(dir.resolve("noexiste.bmp"), dir.resolve("o")));
        assertTrue(lastErr.contains("no existe"), lastErr);

        Path notBmp = file("falso.bmp", "esto no es un bmp, solo texto de relleno para superar los 54 bytes....".getBytes(StandardCharsets.UTF_8));
        assertEquals(Main.EXIT_FAILURE, embed(in, notBmp, out));
        assertTrue(lastErr.contains("no es un BMP válido"), lastErr);
        assertEquals(Main.EXIT_FAILURE, extract(notBmp, dir.resolve("o")));
        assertTrue(lastErr.contains("no es un BMP válido"), lastErr);

        byte[] bits8 = BmpTestData.valid(8, 8).build();
        bits8[28] = 8;
        Path badDepth = file("8bits.bmp", bits8);
        assertEquals(Main.EXIT_FAILURE, embed(in, badDepth, out));
        assertFalse(Files.exists(out));
    }

    @Test
    void unwritableOutputIsReported() throws Exception {
        Path in = file("a.txt", new byte[10]);
        assertEquals(Main.EXIT_FAILURE, embed(in, carrier(20, 20), dir.resolve("no_existe_dir/r.bmp")));
        assertTrue(lastErr.contains("No se pudo escribir"), lastErr);
    }

    @Test
    void imageWithoutHiddenDataFailsAndWritesNothing() throws Exception {
        Path bmp = carrier(64, 64);
        assertEquals(Main.EXIT_FAILURE, extract(bmp, dir.resolve("o")));
        assertTrue(lastErr.contains("inválido"), lastErr);
        assertEquals(Main.EXIT_FAILURE, extract(bmp, dir.resolve("o"), "-pass", "x"));
        assertEquals(0, countFiles("o"));
    }

    private long countFiles(String prefix) throws IOException {
        try (var s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().startsWith(prefix)).count();
        }
    }

    @Test
    void corruptPlainPayloadsAreRejected() throws Exception {
        Lsb1 lsb1 = new Lsb1();
        byte[][] payloads = {
                {0, 0, 0, 2, 1, 2, '.', 't'}, // extensión sin NUL (se acaba la fuente por la capacidad: se rellena abajo)
                {0, 0, 0, 2, 1, 2, 'x', 0}, // extensión sin punto
                {0, 0, 0, 2, 1, 2, '.', (byte) 0xE9, 0}, // byte no ASCII
                {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, // tamaño absurdo
        };
        for (int i = 0; i < payloads.length; i++) {
            byte[] bmpBytes = BmpTestData.valid(8, 4).build(); // 96 bytes -> 12 bytes de capacidad
            var image = ar.edu.itba.stegobmp.bmp.BmpReader.parse(bmpBytes);
            byte[] body = image.copyBody();
            // se deja el resto del cuerpo en bits cero para que no aparezca un NUL accidental
            Arrays.fill(body, (byte) 0xFF);
            byte[] hidden = payloads[i];
            Path bad = file("corrupto" + i + ".bmp", image.withBody(lsb1.embed(body, hidden)).toBytes());
            assertEquals(Main.EXIT_FAILURE, extract(bad, dir.resolve("out" + i)), "caso " + i);
            assertTrue(lastErr.startsWith("Error:"), lastErr);
            assertEquals(0, countFiles("out" + i));
        }
    }

    @Test
    void truncatedEncryptedPayloadIsRejected() throws Exception {
        Path in = file("a.txt", allByteValues(200, 15));
        Path big = carrier(40, 40);
        Path stego = dir.resolve("r.bmp");
        assertEquals(0, embed(in, big, stego, "-pass", "pw"), lastErr);

        // se recorta el cuerpo a un BMP pequeño que conserva el comienzo del mensaje
        var image = ar.edu.itba.stegobmp.bmp.BmpReader.parse(Files.readAllBytes(stego));
        byte[] smallBmp = BmpTestData.valid(8, 4).build();
        var small = ar.edu.itba.stegobmp.bmp.BmpReader.parse(smallBmp);
        byte[] body = Arrays.copyOf(image.copyBody(), small.bodyLength());
        Path truncated = file("truncado.bmp", small.withBody(body).toBytes());
        assertEquals(Main.EXIT_FAILURE, extract(truncated, dir.resolve("o"), "-pass", "pw"));
        assertTrue(lastErr.contains("excede los bytes disponibles"), lastErr);
        assertEquals(0, countFiles("o."));
    }

    // ---- contraseña incorrecta ----

    @Test
    void wrongPasswordInPaddedModesIsUsuallyDetectedAndNeverRecoversTheFile() throws Exception {
        for (String mode : new String[] {"ecb", "cbc"}) {
            byte[] content = allByteValues(150, 16);
            Path in = file("a.txt", content);
            Path stego = dir.resolve("r.bmp");
            assertEquals(0, embed(in, carrier(40, 40), stego, "-m", mode, "-pass", "correcta"), lastErr);
            int failures = 0;
            for (int i = 0; i < 20; i++) {
                Path out = dir.resolve("w" + mode + i);
                if (extract(stego, out, "-m", mode, "-pass", "mala" + i) != 0) {
                    failures++;
                    assertTrue(lastErr.contains("Verifique la contraseña"), lastErr);
                } else {
                    assertFalse(Arrays.equals(content, Files.readAllBytes(Path.of(out + ".txt"))));
                }
            }
            assertTrue(failures >= 18, mode + ": detectadas " + failures + " de 20");
        }
    }

    @Test
    void wrongPasswordInStreamModesCannotBeCryptographicallyDetectedButNeverRecoversTheFile() throws Exception {
        for (String mode : new String[] {"cfb", "ofb"}) {
            byte[] content = allByteValues(150, 17);
            Path in = file("a.txt", content);
            Path stego = dir.resolve("r.bmp");
            assertEquals(0, embed(in, carrier(40, 40), stego, "-m", mode, "-pass", "correcta"), lastErr);
            for (int i = 0; i < 10; i++) {
                Path out = dir.resolve("s" + mode + i);
                int code = extract(stego, out, "-m", mode, "-pass", "mala" + i);
                if (code == 0) {
                    // si por azar el contenido descifrado tiene un formato válido, de todos modos es basura
                    final String prefix = "s" + mode + i;
                    try (var files = Files.list(dir)) {
                        for (Path p : files.filter(x -> x.getFileName().toString().startsWith(prefix)).toList()) {
                            assertFalse(Arrays.equals(content, Files.readAllBytes(p)));
                        }
                    }
                } else {
                    assertEquals(Main.EXIT_FAILURE, code);
                    assertTrue(lastErr.contains("CFB y OFB una contraseña incorrecta no se detecta"), lastErr);
                }
            }
        }
    }

    @Test
    void wrongAlgorithmOrModeDoesNotRecoverTheFile() throws Exception {
        byte[] content = allByteValues(150, 18);
        Path in = file("a.txt", content);
        Path stego = dir.resolve("r.bmp");
        assertEquals(0, embed(in, carrier(40, 40), stego, "-a", "aes256", "-m", "cbc", "-pass", "pw"), lastErr);
        assertNotEquals(0, extract(stego, dir.resolve("x1"), "-a", "aes128", "-m", "cbc", "-pass", "pw"));
        assertNotEquals(0, extract(stego, dir.resolve("x2"), "-a", "aes256", "-m", "ecb", "-pass", "pw"));
        assertNotEquals(0, extract(stego, dir.resolve("x3"))); // sin -pass sobre una imagen cifrada
        assertEquals(0, countFiles("x"));
    }

    @Test
    void passwordNeverAppearsInErrorMessages() throws Exception {
        Path in = file("a.txt", new byte[10]);
        assertEquals(Main.EXIT_FAILURE, embed(in, dir.resolve("noexiste.bmp"), dir.resolve("o.bmp"), "-pass", "S3cr3t0Unico"));
        assertFalse(lastErr.contains("S3cr3t0Unico"));
        Path stego = dir.resolve("r.bmp");
        assertEquals(0, embed(in, carrier(30, 30), stego, "-pass", "otra"), lastErr);
        assertEquals(Main.EXIT_FAILURE, extract(stego, dir.resolve("o"), "-pass", "S3cr3t0Unico"));
        assertFalse(lastErr.contains("S3cr3t0Unico"));
    }

}
