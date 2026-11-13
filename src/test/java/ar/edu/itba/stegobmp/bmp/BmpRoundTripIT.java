package ar.edu.itba.stegobmp.bmp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Integración: leer un BMP desde disco y volver a escribirlo debe dar un archivo idéntico. */
class BmpRoundTripIT {

    @TempDir
    Path dir;

    private void assertRoundTrip(String name, byte[] original) throws Exception {
        Path in = dir.resolve(name + "-in.bmp");
        Path out = dir.resolve(name + "-out.bmp");
        Files.write(in, original);
        BmpWriter.write(BmpReader.read(in), out);
        assertArrayEquals(original, Files.readAllBytes(out), name);
        assertArrayEquals(Files.readAllBytes(in), Files.readAllBytes(out), name + " (disco)");
    }

    @Test
    void roundTripIsByteIdenticalForSupportedVariants() throws Exception {
        assertRoundTrip("sin-padding", BmpTestData.valid(4, 5).build());
        assertRoundTrip("con-padding", BmpTestData.valid(3, 5).build());
        assertRoundTrip("offset-mayor", BmpTestData.valid(3, 5).gap(26).build());
        assertRoundTrip("top-down", BmpTestData.valid(5, -4).build());
        assertRoundTrip("bytes-extra", BmpTestData.valid(3, 3).trailing(100).build());
        assertRoundTrip("sizeimage-inconsistente", BmpTestData.valid(3, 3).sizeImage(12345).build());
        assertRoundTrip("todo-junto", BmpTestData.valid(7, -9).gap(5).trailing(13).build());
        assertRoundTrip("grande", BmpTestData.valid(641, 480).trailing(3).build());
    }

    @Test
    void roundTripOfRealSamplesIfPresent() throws Exception {
        Path samples = Path.of("samples");
        if (!Files.isDirectory(samples)) {
            return;
        }
        try (var stream = Files.list(samples)) {
            List<Path> bmps = stream.filter(p -> p.toString().toLowerCase().endsWith(".bmp")).toList();
            for (Path p : bmps) {
                Path out = dir.resolve("copy-" + p.getFileName());
                BmpWriter.write(BmpReader.read(p), out);
                assertTrue(Files.mismatch(p, out) == -1, "Difieren: " + p);
            }
        }
    }
}
