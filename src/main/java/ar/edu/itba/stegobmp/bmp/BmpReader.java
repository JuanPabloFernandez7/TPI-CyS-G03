package ar.edu.itba.stegobmp.bmp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lee y valida BMP V3 (BITMAPINFOHEADER) de 24 bits sin compresión. */
public final class BmpReader {
    private BmpReader() {
    }

    public static BmpImage read(Path path) throws IOException, BmpFormatException {
        long size = Files.size(path);
        if (size > Integer.MAX_VALUE) {
            throw new BmpFormatException("Archivo demasiado grande para procesar: " + size + " bytes");
        }
        return parse(Files.readAllBytes(path));
    }

    public static BmpImage parse(byte[] data) throws BmpFormatException {
        if (data.length < BmpHeader.SIZE) {
            throw new BmpFormatException("Encabezado incompleto: el archivo tiene " + data.length
                    + " bytes y se requieren al menos " + BmpHeader.SIZE);
        }
        if (data[0] != 'B' || data[1] != 'M') {
            throw new BmpFormatException("Firma incorrecta: se esperaba 'BM'");
        }
        long pixelOffset = u32(data, 10);
        long dibSize = u32(data, 14);
        int width = (int) u32(data, 18);
        int height = (int) u32(data, 22);
        int planes = u16(data, 26);
        int bitsPerPixel = u16(data, 28);
        long compression = u32(data, 30);

        if (dibSize != BmpHeader.DIB_HEADER_SIZE) {
            throw new BmpFormatException("Solo se soporta BMP V3 con DIB de " + BmpHeader.DIB_HEADER_SIZE
                    + " bytes; el archivo tiene " + dibSize);
        }
        if (planes != 1) {
            throw new BmpFormatException("Planos inválidos: " + planes + " (se esperaba 1)");
        }
        if (bitsPerPixel != BmpHeader.BITS_PER_PIXEL) {
            throw new BmpFormatException("Se requieren " + BmpHeader.BITS_PER_PIXEL
                    + " bits por píxel; el archivo tiene " + bitsPerPixel);
        }
        if (compression != 0) {
            throw new BmpFormatException("El BMP está comprimido (compresión " + compression
                    + "); solo se soporta sin compresión (0)");
        }
        if (width <= 0) {
            throw new BmpFormatException("Ancho inválido: " + width);
        }
        if (height == 0) {
            throw new BmpFormatException("Alto inválido: 0");
        }
        if (pixelOffset < BmpHeader.SIZE) {
            throw new BmpFormatException("Offset de píxeles inválido: " + pixelOffset
                    + " (mínimo " + BmpHeader.SIZE + ")");
        }

        BmpHeader header = new BmpHeader(pixelOffset, width, height);
        long bodySize;
        long end;
        try {
            bodySize = header.bodySize();
            end = Math.addExact(pixelOffset, bodySize);
        } catch (ArithmeticException e) {
            throw new BmpFormatException("Dimensiones fuera de rango: " + width + "x" + height);
        }
        if (end > data.length) {
            throw new BmpFormatException("Archivo truncado: se requieren " + end + " bytes (offset "
                    + pixelOffset + " + cuerpo " + bodySize + ") y el archivo tiene " + data.length);
        }
        return new BmpImage(header, data, (int) pixelOffset, (int) bodySize);
    }

    private static int u16(byte[] d, int i) {
        return (d[i] & 0xFF) | (d[i + 1] & 0xFF) << 8;
    }

    private static long u32(byte[] d, int i) {
        return (d[i] & 0xFFL) | (d[i + 1] & 0xFFL) << 8 | (d[i + 2] & 0xFFL) << 16 | (d[i + 3] & 0xFFL) << 24;
    }
}
