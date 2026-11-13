package ar.edu.itba.stegobmp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.itba.stegobmp.bmp.BmpImage;
import ar.edu.itba.stegobmp.bmp.BmpReader;
import ar.edu.itba.stegobmp.bmp.BmpTestData;
import ar.edu.itba.stegobmp.steganography.CarrierLayout;
import ar.edu.itba.stegobmp.steganography.Lsbi;
import java.io.ByteArrayOutputStream;
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

/** Complete LSBI flows through the CLI application with real BMP and payload files. */
class EndToEndLsbiTest {
    @TempDir
    Path dir;

    private String lastErr = "";

    private int main(String... args) {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(args, new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        lastErr = err.toString(StandardCharsets.UTF_8);
        return code;
    }

    private Path carrier(int width, int height) throws Exception {
        Path path = dir.resolve("portador.bmp");
        Files.write(path, BmpTestData.valid(width, height).build());
        return path;
    }

    private Path file(String name, byte[] content) throws Exception {
        Path path = dir.resolve(name);
        Files.write(path, content);
        return path;
    }

    private int embed(Path input, Path bmp, Path output, String... extra) {
        List<String> args = new ArrayList<>(List.of("-embed", "-in", input.toString(), "-p", bmp.toString(),
                "-out", output.toString(), "-steg", "LSBI"));
        args.addAll(Arrays.asList(extra));
        return main(args.toArray(String[]::new));
    }

    private int extract(Path bmp, Path output, String... extra) {
        List<String> args = new ArrayList<>(List.of("-extract", "-p", bmp.toString(), "-out", output.toString(),
                "-steg", "LSBI"));
        args.addAll(Arrays.asList(extra));
        return main(args.toArray(String[]::new));
    }

    @Test
    void plainRoundTripWithExactCapacityPreservesBmpMetadataAndNonCarrierBytes() throws Exception {
        byte[] content = {0x5A};
        Path input = file("archivo.txt", content);
        Path originalPath = dir.resolve("original.bmp");
        Files.write(originalPath, BmpTestData.valid(1, -41).gap(9).trailing(11).build());
        byte[] originalBytes = Files.readAllBytes(originalPath);
        BmpImage original = BmpReader.read(originalPath);
        Path output = dir.resolve("resultado.bmp");

        assertEquals(0, embed(input, originalPath, output), lastErr);
        Path recovered = dir.resolve("recuperado");
        assertEquals(0, extract(output, recovered), lastErr);
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("recuperado.txt")));

        BmpImage encoded = BmpReader.read(output);
        assertArrayEquals(Arrays.copyOfRange(originalBytes, 0, original.bodyOffset()),
                Arrays.copyOfRange(encoded.toBytes(), 0, encoded.bodyOffset()));
        assertArrayEquals(original.gapBytes(), encoded.gapBytes());
        assertArrayEquals(original.trailingBytes(), encoded.trailingBytes());
        byte[] before = original.copyBody();
        byte[] after = encoded.copyBody();
        CarrierLayout layout = layout(original);
        for (int index = 4; index < before.length; index++) {
            if (!layout.isBlueOrGreenComponent(index)) {
                assertEquals(before[index], after[index], "Non-data byte changed at " + index);
            }
        }
    }

    @Test
    void payloadWithExtensionlessAndEmptyFilesRoundTrips() throws Exception {
        Path bmp = carrier(32, 32);
        Path noExtension = file("archivo", new byte[] {0, 1, 2, (byte) 0xFF});
        assertEquals(0, embed(noExtension, bmp, dir.resolve("sin-extension.bmp")), lastErr);
        assertEquals(0, extract(dir.resolve("sin-extension.bmp"), dir.resolve("restaurado")), lastErr);
        assertArrayEquals(Files.readAllBytes(noExtension), Files.readAllBytes(dir.resolve("restaurado")));

        Path empty = file("vacio.dat", new byte[0]);
        assertEquals(0, embed(empty, bmp, dir.resolve("vacio.bmp")), lastErr);
        assertEquals(0, extract(dir.resolve("vacio.bmp"), dir.resolve("vacio-rec")), lastErr);
        assertArrayEquals(new byte[0], Files.readAllBytes(dir.resolve("vacio-rec.dat")));
    }

    @Test
    void oneByteBeyondCapacityFailsBeforeWritingOutput() throws Exception {
        Path input = file("archivo.txt", new byte[] {1});
        Path bmp = dir.resolve("capacidad.bmp");
        Files.write(bmp, BmpTestData.valid(1, 40).build());
        Path output = dir.resolve("no-debe-existir.bmp");

        assertEquals(1, embed(input, bmp, output));
        assertTrue(lastErr.contains("se necesitan 10 bytes"));
        assertTrue(lastErr.contains("capacidad máxima es 9 bytes"));
        assertFalse(Files.exists(output));
    }

    @ParameterizedTest(name = "{0}-{1}")
    @MethodSource("allCipherCombinations")
    void encryptedRoundTripForEveryAlgorithmAndMode(String algorithm, String mode) throws Exception {
        byte[] content = new byte[423];
        new Random(0x4C534249L).nextBytes(content);
        Path input = file("encrypted.bin", content);
        Path output = dir.resolve("encrypted.bmp");
        String[] options = {"-a", algorithm, "-m", mode, "-pass", "contraseña-ñ"};

        assertEquals(0, embed(input, carrier(64, -64), output, options), lastErr);
        assertEquals(0, extract(output, dir.resolve("decoded"), options), lastErr);
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("decoded.bin")));
    }

    static Stream<Arguments> allCipherCombinations() {
        List<Arguments> combinations = new ArrayList<>();
        for (String algorithm : new String[] {"aes128", "aes192", "aes256", "3des"}) {
            for (String mode : new String[] {"ecb", "cbc", "cfb", "ofb"}) {
                combinations.add(Arguments.of(algorithm, mode));
            }
        }
        return combinations.stream();
    }

    @Test
    void allCliEncryptionDefaultsMatchTheirExplicitSettings() throws Exception {
        Path input = file("default.txt", new byte[] {1, 2, 3});
        Path bmp = carrier(32, 32);
        String[][] defaults = {
                {"-pass", "pw"},
                {"-a", "3des", "-pass", "pw"},
                {"-m", "ofb", "-pass", "pw"}
        };
        String[][] explicit = {
                {"-a", "aes128", "-m", "cbc", "-pass", "pw"},
                {"-a", "3des", "-m", "cbc", "-pass", "pw"},
                {"-a", "aes128", "-m", "ofb", "-pass", "pw"}
        };

        for (int index = 0; index < defaults.length; index++) {
            Path defaultOutput = dir.resolve("default-" + index + ".bmp");
            Path explicitOutput = dir.resolve("explicit-" + index + ".bmp");
            assertEquals(0, embed(input, bmp, defaultOutput, defaults[index]), lastErr);
            assertEquals(0, embed(input, bmp, explicitOutput, explicit[index]), lastErr);
            assertArrayEquals(Files.readAllBytes(defaultOutput), Files.readAllBytes(explicitOutput));
            Path recovered = dir.resolve("default-restored-" + index);
            assertEquals(0, extract(defaultOutput, recovered, defaults[index]), lastErr);
            assertArrayEquals(Files.readAllBytes(input),
                    Files.readAllBytes(dir.resolve("default-restored-" + index + ".txt")));
        }
    }

    @Test
    void invalidBmpAndCorruptPayloadProduceOperationErrors() throws Exception {
        Path invalidBmp = dir.resolve("invalid.bmp");
        Files.write(invalidBmp, new byte[] {'N', 'O', 'T', 'B', 'M', 'P'});
        assertEquals(1, extract(invalidBmp, dir.resolve("invalid-out")));
        assertTrue(lastErr.contains("no es un BMP válido"));

        Path carrier = dir.resolve("corrupt-carrier.bmp");
        BmpImage image = BmpReader.parse(BmpTestData.valid(32, 32).build());
        CarrierLayout layout = layout(image);
        byte[] corruptedPayload = {0, 0, 0, 1, 65, 66};
        byte[] body = new Lsbi().embed(image.copyBody(), corruptedPayload, layout);
        Files.write(carrier, image.withBody(body).toBytes());
        assertEquals(1, extract(carrier, dir.resolve("corrupt-out")));
        assertTrue(lastErr.contains("payload oculto es inválido"));
    }

    private static CarrierLayout layout(BmpImage image) {
        return new CarrierLayout(image.header().width(), image.header().rowCount(), image.header().stride());
    }
}
