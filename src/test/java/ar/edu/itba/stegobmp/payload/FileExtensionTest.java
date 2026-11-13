package ar.edu.itba.stegobmp.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.itba.stegobmp.payload.PayloadFormatException.Reason;
import org.junit.jupiter.api.Test;

class FileExtensionTest {

    private static String ext(String fileName) throws Exception {
        return FileExtension.fromFileName(fileName).text();
    }

    @Test
    void fromFileName() throws Exception {
        assertEquals(".txt", ext("mensaje1.txt"));
        assertEquals(".gz", ext("a.tar.gz"));
        assertEquals("", ext("archivo"));
        assertEquals("", ext("archivo."));
        assertEquals(".bashrc", ext(".bashrc"));
        assertEquals(".JPG", ext("foto.JPG"));
        assertEquals(".b c", ext("a.b c"));
        assertEquals(".tar gz", ext("a.tar gz"));
    }

    @Test
    void directoriesAreIgnored() throws Exception {
        assertEquals("", ext("/tmp/dir.v2/archivo"));
        assertEquals(".png", ext("/tmp/dir.v2/foto.png"));
        assertEquals("", ext("C:\\datos.v2\\archivo"));
    }

    @Test
    void nonAsciiExtensionIsRejected() {
        PayloadFormatException e = assertThrows(PayloadFormatException.class, () -> ext("a.café"));
        assertEquals(Reason.EXTENSION_INVALID_CHAR, e.reason());
        assertTrue(e.getMessage().contains(".café"), e.getMessage());
    }

    @Test
    void ofValidatesDotAndAlphabet() throws Exception {
        assertEquals(Reason.EXTENSION_MISSING_DOT,
                assertThrows(PayloadFormatException.class, () -> FileExtension.of("txt")).reason());
        assertEquals(Reason.EXTENSION_MISSING_DOT,
                assertThrows(PayloadFormatException.class, () -> FileExtension.of("")).reason());
        for (String bad : new String[] {".a/b", ".a\\b", ".a\u0000b", ".a\u0007", ".é"}) {
            assertEquals(Reason.EXTENSION_INVALID_CHAR,
                    assertThrows(PayloadFormatException.class, () -> FileExtension.of(bad)).reason(), bad);
        }
        assertEquals(FileExtension.none(), FileExtension.of("."));
        assertEquals(".tar gz", FileExtension.of(".tar gz").text());
    }

    @Test
    void encodingAndLengths() throws Exception {
        assertEquals(5, FileExtension.of(".txt").encodedLength());
        assertArrayEquals(new byte[] {'.', 't', 'x', 't', 0}, FileExtension.of(".txt").toBytes());
        assertEquals(2, FileExtension.none().encodedLength());
        assertArrayEquals(new byte[] {'.', 0}, FileExtension.none().toBytes());
        assertTrue(FileExtension.none().isEmpty());
    }

    @Test
    void appendTo() throws Exception {
        assertEquals("mensaje1.txt", FileExtension.of(".txt").appendTo("mensaje1"));
        assertEquals("mensaje1", FileExtension.none().appendTo("mensaje1"));
    }
}
