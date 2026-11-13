package ar.edu.itba.stegobmp.bmp;

/**
 * Campos relevantes del encabezado de 54 bytes (BITMAPFILEHEADER + BITMAPINFOHEADER).
 * Todos los valores del archivo BMP están en little endian.
 *
 * @param pixelOffset posición (uint32) donde empieza el cuerpo, según el campo bfOffBits
 * @param width ancho en píxeles (> 0)
 * @param height alto con signo; negativo indica BMP top-down (el cuerpo igual se recorre en orden físico)
 */
public record BmpHeader(long pixelOffset, int width, int height) {
    public static final int SIZE = 54;
    public static final int DIB_HEADER_SIZE = 40;
    public static final int BITS_PER_PIXEL = 24;

    /** Cantidad de filas almacenadas. */
    public long rowCount() {
        return Math.abs((long) height);
    }

    /** Bytes por fila incluyendo padding: 4 * ceil(width * 3 / 4). */
    public long stride() {
        long rowBytes = (long) width * 3;
        return (rowBytes + 3) / 4 * 4;
    }

    /** Tamaño del cuerpo (píxeles y padding); lanza ArithmeticException si desborda un long. */
    public long bodySize() {
        return Math.multiplyExact(stride(), rowCount());
    }
}
