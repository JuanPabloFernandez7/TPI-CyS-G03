package ar.edu.itba.stegobmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Verifica el JAR empaquetado (fase integration-test de {@code mvn verify}). */
class PackagedJarIT {

    private record Result(int exitCode, String stdout, String stderr) {
    }

    private static Result runJar(String... args) throws IOException, InterruptedException {
        Path jar = Path.of(System.getProperty("stegobmp.jar"));
        assertTrue(Files.isRegularFile(jar), "No existe el JAR: " + jar);
        Path javaBin = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> command = new ArrayList<>(List.of(javaBin.toString(),
                "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar", jar.toString()));
        command.addAll(List.of(args));
        Path outFile = Files.createTempFile("stegobmp-out", ".txt");
        Path errFile = Files.createTempFile("stegobmp-err", ".txt");
        try {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(outFile.toFile())
                    .redirectError(errFile.toFile())
                    .start();
            int exit = process.waitFor();
            return new Result(exit, Files.readString(outFile, StandardCharsets.UTF_8),
                    Files.readString(errFile, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(outFile);
            Files.deleteIfExists(errFile);
        }
    }

    @Test
    void helpExitsZero() throws Exception {
        Result r = runJar("--help");
        assertEquals(0, r.exitCode());
        assertTrue(r.stdout().contains("stegobmp -embed"));
    }

    @Test
    void invalidArgumentsExitWithUsageError() throws Exception {
        Result r = runJar("-embed", "-foo");
        assertEquals(2, r.exitCode());
        assertTrue(r.stderr().contains("Argumento desconocido: -foo"));
    }

    @Test
    void lsbiArgumentsReachTheOperationInsteadOfReturningNotImplemented() throws Exception {
        Result r = runJar("-extract", "-p", "a.bmp", "-out", "o", "-steg", "LSBI", "-pass", "-a");
        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().startsWith("Error:"), r.stderr());
        assertTrue(!r.stderr().contains("todavía no"), r.stderr());
    }

    private static void roundTrip(Path dir, String name, String[] extra) throws Exception {
        roundTrip(dir, name, "LSB1", extra);
    }

    private static void roundTrip(Path dir, String name, String steg, String[] extra) throws Exception {
        byte[] content = new byte[300];
        new java.util.Random(7).nextBytes(content);
        Path in = dir.resolve(name);
        Files.write(in, content);
        Path bmp = dir.resolve("imagen.bmp");
        int width = "LSBI".equals(steg) ? 48 : 32;
        Files.write(bmp, ar.edu.itba.stegobmp.bmp.BmpTestData.valid(width, 32).build());
        Path result = dir.resolve("resultado.bmp");
        Path recovered = dir.resolve("recuperado");

        List<String> embed = new ArrayList<>(List.of("-embed", "-in", in.toString(), "-p", bmp.toString(),
                "-out", result.toString(), "-steg", steg));
        embed.addAll(List.of(extra));
        Result e = runJar(embed.toArray(String[]::new));
        assertEquals(0, e.exitCode(), e.stderr());

        List<String> extract = new ArrayList<>(List.of("-extract", "-p", result.toString(),
                "-out", recovered.toString(), "-steg", steg));
        extract.addAll(List.of(extra));
        Result x = runJar(extract.toArray(String[]::new));
        assertEquals(0, x.exitCode(), x.stderr());

        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
        assertEquals(java.util.Arrays.hashCode(content),
                java.util.Arrays.hashCode(Files.readAllBytes(dir.resolve("recuperado" + ext))));
        org.junit.jupiter.api.Assertions.assertArrayEquals(content, Files.readAllBytes(dir.resolve("recuperado" + ext)));
    }

    @Test
    void embedAndExtractWithoutEncryption(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        roundTrip(dir, "archivo.txt", new String[0]);
    }

    @Test
    void embedAndExtractWithAes128Cbc(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        roundTrip(dir, "archivo.txt", new String[] {"-a", "aes128", "-m", "cbc", "-pass", "password"});
    }

    @Test
    void embedAndExtractWithDefaultsAnd3desCfb(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        roundTrip(dir, "dato.png", new String[] {"-pass", "password"});
        roundTrip(dir, "sin_extension", new String[] {"-a", "3des", "-m", "cfb", "-pass", "password"});
    }

    @Test
    void embedAndExtractWithLsb4(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        roundTrip(dir, "archivo.txt", "LSB4", new String[] {"-a", "aes128", "-m", "cbc", "-pass", "password"});
    }

    @Test
    void embedAndExtractWithLsbi(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        roundTrip(dir, "archivo.txt", "LSBI", new String[] {"-a", "3des", "-m", "ofb", "-pass", "password"});
    }

    @Test
    void embedAndExtractWithLsbiWithoutEncryption(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        roundTrip(dir, "archivo.txt", "LSBI", new String[0]);
    }

    @ParameterizedTest
    @CsvSource({"108,LSB1", "108,LSB4", "108,LSBI", "124,LSB1", "124,LSB4", "124,LSBI"})
    void extendedHeaderRoundTripPreservesNonPixelBytes(int dibSize, String method,
            @org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        int width = 33;
        int height = 64;
        int offset = 14 + dibSize + 7;
        int bodyLength = 100 * height;
        byte[] carrier = ar.edu.itba.stegobmp.bmp.BmpTestData.valid(width, height)
                .gap(offset - 54).trailing(11).build();
        ByteBuffer.wrap(carrier).order(ByteOrder.LITTLE_ENDIAN).putInt(14, dibSize);
        Arrays.fill(carrier, 54, 14 + dibSize, (byte) 0);
        byte[] content = "Contenido para verificar V4/V5 y padding.".getBytes(StandardCharsets.UTF_8);
        Path in = dir.resolve("archivo.txt");
        Path bmp = dir.resolve("portador.bmp");
        Path stego = dir.resolve("stego.bmp");
        Path out = dir.resolve("recuperado");
        Files.write(in, content);
        Files.write(bmp, carrier);
        Result embed = runJar("-embed", "-in", in.toString(), "-p", bmp.toString(), "-out", stego.toString(),
                "-steg", method, "-a", "aes128", "-m", "cbc", "-pass", "password");
        assertEquals(0, embed.exitCode(), embed.stderr());
        byte[] result = Files.readAllBytes(stego);
        org.junit.jupiter.api.Assertions.assertArrayEquals(Arrays.copyOfRange(carrier, 0, offset),
                Arrays.copyOfRange(result, 0, offset));
        org.junit.jupiter.api.Assertions.assertArrayEquals(Arrays.copyOfRange(carrier, offset + bodyLength, carrier.length),
                Arrays.copyOfRange(result, offset + bodyLength, result.length));
        Result extract = runJar("-extract", "-p", stego.toString(), "-out", out.toString(), "-steg", method,
                "-a", "aes128", "-m", "cbc", "-pass", "password");
        assertEquals(0, extract.exitCode(), extract.stderr());
        org.junit.jupiter.api.Assertions.assertArrayEquals(content, Files.readAllBytes(dir.resolve("recuperado.txt")));
    }

    @Test
    void extractsAndDecodesAllOfficialExamples(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path samples = Path.of("docs/docs_catedra/ejemplo");
        String[][] cases = {
                {"ladoLSB1.bmp", "LSB1"},
                {"ladoLSB4.bmp", "LSB4"},
                {"ladoLSBI.bmp", "LSBI"},
                {"ladoLSB1aes128cbc.bmp", "LSB1", "-a", "aes128", "-m", "cbc", "-pass", "margarita"},
                {"ladoLSBIaes256ofb.bmp", "LSBI", "-a", "aes256", "-m", "ofb", "-pass", "margarita"},
                {"ladoLSBIdescfb.bmp", "LSBI", "-a", "3des", "-m", "cfb", "-pass", "margarita"}
        };
        for (String[] sample : cases) {
            Path out = dir.resolve(sample[0]);
            List<String> args = new ArrayList<>(List.of("-extract", "-p", samples.resolve(sample[0]).toString(),
                    "-out", out.toString(), "-steg", sample[1]));
            args.addAll(Arrays.asList(sample).subList(2, sample.length));
            Result result = runJar(args.toArray(String[]::new));
            assertEquals(0, result.exitCode(), sample[0] + ": " + result.stderr());
            Path png = Path.of(out + ".png");
            byte[] data = Files.readAllBytes(png);
            assertEquals("b93be15382f35f099ca286241dd7bb6e681d5bce8d315ac0b6240f0b516f549d",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)), sample[0]);
            assertTrue(ImageIO.read(png.toFile()) != null, sample[0] + ": PNG inválido");
        }
    }

    @Test
    void operationErrorsExitWithOne(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Result r = runJar("-extract", "-p", dir.resolve("no_existe.bmp").toString(),
                "-out", dir.resolve("o").toString(), "-steg", "LSB1");
        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().startsWith("Error:"), r.stderr());
    }
}
