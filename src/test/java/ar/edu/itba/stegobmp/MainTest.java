package ar.edu.itba.stegobmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MainTest {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void helpPrintsUsageToStdoutWithExitZero() {
        assertEquals(Main.EXIT_OK, run("--help"));
        assertEquals(Main.EXIT_OK, run("-h"));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("stegobmp -embed"));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void usageErrorPrintsMessageAndUsageToStderr() {
        assertEquals(Main.EXIT_USAGE_ERROR, run("-embed", "-foo"));
        String stderr = err.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.contains("Error: Argumento desconocido: -foo"));
        assertTrue(stderr.contains("Uso:"));
        assertEquals("", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void noArgumentsIsUsageError() {
        assertEquals(Main.EXIT_USAGE_ERROR, run());
    }

    @Test
    void lsbiArgumentsReachTheOperation() {
        assertEquals(Main.EXIT_FAILURE,
                run("-extract", "-p", "a.bmp", "-out", "o", "-steg", "LSBI"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Error:"));
        assertFalse(err.toString(StandardCharsets.UTF_8).contains("no está implementada"));
    }
}
