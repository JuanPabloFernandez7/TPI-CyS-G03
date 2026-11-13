package ar.edu.itba.stegobmp.payload;

import ar.edu.itba.stegobmp.payload.PayloadFormatException.Reason;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Extensión de archivo tal como se oculta: comienza con '.' y termina con NUL. La extensión vacía
 * (archivo sin extensión) se codifica como {@code ".\0"} (decisión provisional A1).
 */
public final class FileExtension {
    private static final FileExtension NONE = new FileExtension("");

    private final String text;

    private FileExtension(String text) {
        this.text = text;
    }

    public static FileExtension none() {
        return NONE;
    }

    /** Extensión con el punto inicial, p. ej. {@code ".txt"}. */
    public static FileExtension of(String text) throws PayloadFormatException {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty() || text.charAt(0) != '.') {
            throw new PayloadFormatException(Reason.EXTENSION_MISSING_DOT,
                    "La extensión debe comenzar con '.': \"" + text + "\".");
        }
        if (text.length() == 1) {
            return NONE;
        }
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!isAllowed(c)) {
                throw new PayloadFormatException(Reason.EXTENSION_INVALID_CHAR,
                        "La extensión \"" + text + "\" contiene un carácter inválido (U+"
                                + String.format("%04X", (int) c) + ").");
            }
        }
        return new FileExtension(text);
    }

    /** Sufijo desde el último '.' del nombre (sin directorios); sin '.' o con '.' final no hay extensión. */
    public static FileExtension fromFileName(String fileName) throws PayloadFormatException {
        Objects.requireNonNull(fileName, "fileName");
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String name = fileName.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return NONE;
        }
        return of(name.substring(dot));
    }

    /** ASCII imprimible 0x20..0x7E salvo los separadores de ruta. */
    static boolean isAllowedByte(int b) {
        return b >= 0x20 && b <= 0x7E && b != '/' && b != '\\';
    }

    private static boolean isAllowed(char c) {
        return isAllowedByte(c);
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    /** Texto con el punto inicial, o cadena vacía si no hay extensión. */
    public String text() {
        return text;
    }

    /** Longitud codificada: punto + caracteres + NUL (mínimo 2). */
    public int encodedLength() {
        return text.isEmpty() ? 2 : text.length() + 1;
    }

    public byte[] toBytes() {
        if (text.isEmpty()) {
            return new byte[] {'.', 0};
        }
        byte[] ascii = text.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[ascii.length + 1];
        System.arraycopy(ascii, 0, out, 0, ascii.length);
        return out;
    }

    public String appendTo(String path) {
        return path + text;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FileExtension other && other.text.equals(text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return text.isEmpty() ? "(sin extensión)" : text;
    }
}
