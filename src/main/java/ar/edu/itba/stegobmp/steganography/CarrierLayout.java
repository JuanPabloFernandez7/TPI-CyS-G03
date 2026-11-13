package ar.edu.itba.stegobmp.steganography;

/**
 * Geometry needed by algorithms that select pixel components from a serialized image body.
 * The body is a sequence of rows; each row contains {@code width * 3} BGR bytes followed by padding.
 */
public record CarrierLayout(int width, long rows, long rowStride) {
    public CarrierLayout {
        if (width <= 0) {
            throw new IllegalArgumentException("Ancho de portador inválido: " + width);
        }
        if (rows <= 0) {
            throw new IllegalArgumentException("Cantidad de filas inválida: " + rows);
        }
        long pixelBytes = Math.multiplyExact((long) width, 3L);
        if (rowStride < pixelBytes) {
            throw new IllegalArgumentException(
                    "Stride de fila " + rowStride + " menor que los bytes de píxeles " + pixelBytes);
        }
        Math.multiplyExact(rows, rowStride);
    }

    public long bodyLength() {
        return Math.multiplyExact(rows, rowStride);
    }

    public long pixelBytesPerRow() {
        return (long) width * 3;
    }

    /** True for a B or G component byte, false for R and row padding. */
    public boolean isBlueOrGreenComponent(long bodyIndex) {
        if (bodyIndex < 0 || bodyIndex >= bodyLength()) {
            return false;
        }
        long column = bodyIndex % rowStride;
        return column < pixelBytesPerRow() && column % 3 != 2;
    }

    public long dataComponentCountAfterPrefix(int prefixBytes) {
        if (prefixBytes < 0) {
            throw new IllegalArgumentException("Prefijo negativo: " + prefixBytes);
        }
        long count = Math.multiplyExact(Math.multiplyExact((long) width, rows), 2L);
        long prefixEnd = Math.min((long) prefixBytes, bodyLength());
        for (long index = 0; index < prefixEnd; index++) {
            if (isBlueOrGreenComponent(index)) {
                count--;
            }
        }
        return count;
    }

    public long capacityBytesAfterPrefix(int prefixBytes) {
        return dataComponentCountAfterPrefix(prefixBytes) / Byte.SIZE;
    }
}
