package ar.edu.itba.stegobmp.payload;

import ar.edu.itba.stegobmp.payload.PayloadFormatException.Reason;
import java.io.ByteArrayOutputStream;

/**
 * Único lugar que conoce el formato del payload (consigna §5.3, §5.4):
 * {@code u32be(F) || datos || extensión\0} y, con cifrado, {@code u32be(C) || ciphertext}.
 * No cifra ni transporta: solo serializa y deserializa.
 */
public final class PayloadCodec {
    private static final int SIZE_FIELD = 4;
    private static final long MAX_LENGTH = Integer.MAX_VALUE;

    private PayloadCodec() {}

    /** {@code u32be(F) || datos || extensión}; es también el plaintext que se cifra. */
    public static byte[] serializeFile(HiddenFile file) throws PayloadFormatException {
        long total = plainStreamLength(file);
        byte[] data = file.copyData();
        byte[] ext = file.extension().toBytes();
        byte[] out = new byte[(int) total];
        writeU32(out, 0, data.length);
        System.arraycopy(data, 0, out, SIZE_FIELD, data.length);
        System.arraycopy(ext, 0, out, SIZE_FIELD + data.length, ext.length);
        return out;
    }

    /** Lee un archivo; se detiene en el NUL e ignora lo que quede en la fuente. */
    public static HiddenFile readFile(ByteSource source) throws PayloadFormatException {
        if (source.remainingBytes() < SIZE_FIELD) {
            throw new PayloadFormatException(Reason.TRUNCATED,
                    "Payload truncado: no hay 4 bytes para el tamaño (disponibles: "
                            + source.remainingBytes() + ").");
        }
        long size = readU32(source.readBytes(SIZE_FIELD));
        if (size > MAX_LENGTH) {
            throw new PayloadFormatException(Reason.SIZE_INVALID, "Tamaño inválido: " + size + ".");
        }
        if (size > source.remainingBytes()) {
            throw new PayloadFormatException(Reason.SIZE_EXCEEDS_AVAILABLE,
                    "El tamaño declarado (" + size + ") excede los bytes disponibles ("
                            + source.remainingBytes() + "). Payload inconsistente.");
        }
        byte[] data = source.readBytes((int) size);
        return new HiddenFile(data, readExtension(source));
    }

    /** Parsea un plaintext completo; rechaza cualquier byte posterior al NUL. */
    public static HiddenFile parseFile(byte[] plaintext) throws PayloadFormatException {
        ByteArraySource source = new ByteArraySource(plaintext);
        HiddenFile file = readFile(source);
        if (source.remainingBytes() > 0) {
            throw new PayloadFormatException(Reason.TRAILING_BYTES,
                    "Hay " + source.remainingBytes() + " bytes después de la extensión.");
        }
        return file;
    }

    /** {@code u32be(C) || ciphertext}; C es el tamaño del cifrado, no el del archivo. */
    public static byte[] frameCiphertext(byte[] ciphertext) throws PayloadFormatException {
        long total = framedLength(ciphertext.length);
        byte[] out = new byte[(int) total];
        writeU32(out, 0, ciphertext.length);
        System.arraycopy(ciphertext, 0, out, SIZE_FIELD, ciphertext.length);
        return out;
    }

    /** Lee {@code u32be(C)} y luego C bytes; valida C antes de reservar memoria. */
    public static byte[] readCiphertextBlock(ByteSource source) throws PayloadFormatException {
        if (source.remainingBytes() < SIZE_FIELD) {
            throw new PayloadFormatException(Reason.TRUNCATED,
                    "Payload truncado: no hay 4 bytes para el tamaño cifrado (disponibles: "
                            + source.remainingBytes() + ").");
        }
        long size = readU32(source.readBytes(SIZE_FIELD));
        if (size == 0) {
            throw new PayloadFormatException(Reason.CIPHERTEXT_SIZE_ZERO, "Tamaño cifrado inválido: 0.");
        }
        if (size > MAX_LENGTH) {
            throw new PayloadFormatException(Reason.SIZE_INVALID, "Tamaño cifrado inválido: " + size + ".");
        }
        if (size > source.remainingBytes()) {
            throw new PayloadFormatException(Reason.SIZE_EXCEEDS_AVAILABLE,
                    "El tamaño cifrado declarado (" + size + ") excede los bytes disponibles ("
                            + source.remainingBytes() + "). Payload inconsistente.");
        }
        return source.readBytes((int) size);
    }

    public static long plainStreamLength(HiddenFile file) throws PayloadFormatException {
        return plainStreamLength(file.dataLength(), file.extension().encodedLength());
    }

    /** {@code 4 + F + E}; {@code FILE_TOO_LARGE} si no cabe en un arreglo de Java. */
    public static long plainStreamLength(long dataLength, int extensionEncodedLength)
            throws PayloadFormatException {
        if (dataLength < 0 || extensionEncodedLength < 2) {
            throw new IllegalArgumentException("longitudes inválidas");
        }
        return checkedTotal(Math.addExact(SIZE_FIELD, dataLength) + extensionEncodedLength);
    }

    /** {@code 4 + C}; rechaza C = 0 y totales que no caben en un arreglo de Java. */
    public static long framedLength(long ciphertextLength) throws PayloadFormatException {
        if (ciphertextLength < 0) {
            throw new IllegalArgumentException("longitud negativa: " + ciphertextLength);
        }
        if (ciphertextLength == 0) {
            throw new PayloadFormatException(Reason.CIPHERTEXT_SIZE_ZERO, "Tamaño cifrado inválido: 0.");
        }
        return checkedTotal(SIZE_FIELD + ciphertextLength);
    }

    private static long checkedTotal(long total) throws PayloadFormatException {
        if (total > MAX_LENGTH) {
            throw new PayloadFormatException(Reason.FILE_TOO_LARGE,
                    "El archivo es demasiado grande para el formato (" + total + " bytes).");
        }
        return total;
    }

    private static FileExtension readExtension(ByteSource source) throws PayloadFormatException {
        if (source.remainingBytes() == 0) {
            throw new PayloadFormatException(Reason.TRUNCATED, "Payload truncado: falta la extensión.");
        }
        int first = source.readByte();
        if (first != '.') {
            throw new PayloadFormatException(Reason.EXTENSION_MISSING_DOT,
                    "La extensión debe comenzar con '.' (se leyó " + hex(first) + ").");
        }
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        text.write('.');
        while (true) {
            if (source.remainingBytes() == 0) {
                throw new PayloadFormatException(Reason.EXTENSION_UNTERMINATED,
                        "La extensión no termina en NUL.");
            }
            int b = source.readByte();
            if (b == 0) {
                break;
            }
            if (!FileExtension.isAllowedByte(b)) {
                throw new PayloadFormatException(Reason.EXTENSION_INVALID_CHAR,
                        "Carácter inválido " + hex(b) + " en la extensión.");
            }
            text.write(b);
        }
        return FileExtension.of(text.toString(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static String hex(int b) {
        return String.format("0x%02X", b);
    }

    private static void writeU32(byte[] target, int offset, long value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static long readU32(byte[] b) {
        return ((long) (b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }
}
