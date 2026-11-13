package ar.edu.itba.stegobmp.steganography;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CapacityMathTest {

    /** Búsqueda por fuerza bruta del mayor F tal que el flujo cabe, sin usar las fórmulas cerradas. */
    private static long bruteForceMaxFile(long nMax, int ext, int block, boolean padded) {
        long best = -1;
        for (long f = 0; f <= nMax; f++) {
            long structure = 4 + f + ext;
            long stream;
            if (block == 0) {
                stream = structure;
            } else if (padded) {
                stream = 4 + (structure / block + 1) * block;
            } else {
                stream = 4 + structure;
            }
            if (stream <= nMax) {
                best = f;
            }
        }
        return best;
    }

    // Caso 19: ejemplo 100x100 del diseño: stride 300, cuerpo 30.000, N_max 3.750
    @Test
    void designExamples() {
        long body = 300L * 100;
        assertEquals(3750, CapacityMath.maxStreamBytes(body));
        assertEquals(3741, CapacityMath.maxFileSizeUnencrypted(body, 5));
        assertEquals(3734, CapacityMath.maxFileSizePadded(body, 5, 16));
        assertEquals(3734, CapacityMath.maxFileSizePadded(body, 5, 8));
        assertEquals(3737, CapacityMath.maxFileSizeUnpadded(body, 5));
    }

    @Test
    void formulasMatchBruteForce() {
        for (long body = 0; body <= 8 * 70; body += 3) {
            long nMax = body / 8;
            for (int ext : new int[] {2, 5, 6}) {
                assertEquals(bruteForceMaxFile(nMax, ext, 0, false),
                        Math.max(-1, CapacityMath.maxFileSizeUnencrypted(body, ext)), "plain " + body);
                for (int block : new int[] {8, 16}) {
                    assertEquals(bruteForceMaxFile(nMax, ext, block, true),
                            Math.max(-1, CapacityMath.maxFileSizePadded(body, ext, block)),
                            "padded " + body + " B" + block);
                }
                assertEquals(bruteForceMaxFile(nMax, ext, 1, false),
                        Math.max(-1, CapacityMath.maxFileSizeUnpadded(body, ext)), "unpadded " + body);
            }
        }
    }

    @Test
    void tooSmallBodyGivesNegativeResult() {
        assertTrue(CapacityMath.maxFileSizeUnencrypted(8, 5) < 0);
        assertTrue(CapacityMath.maxFileSizePadded(8, 5, 16) < 0);
        assertTrue(CapacityMath.maxFileSizeUnpadded(8, 5) < 0);
    }

    @Test
    void largestFileExactlyFitsInLsb1() throws Exception {
        Lsb1 lsb1 = new Lsb1();
        int body = 8 * 100;
        int f = (int) CapacityMath.maxFileSizeUnencrypted(body, 5);
        byte[] stream = new byte[4 + f + 5];
        assertArrayEquals(stream, lsb1.newReader(lsb1.embed(new byte[body], stream)).readBytes(stream.length));
        assertEquals(100, stream.length);
    }
}
