package ar.edu.itba.stegobmp.steganography;

/**
 * Orden en que se recorren los 8 bits de cada byte del mensaje al insertarlos/extraerlos.
 *
 * <p>DECISIÓN PROVISIONAL: la consigna no especifica este orden. Se usa MSB primero (el primer bit
 * del mensaje es el bit 7 del primer byte). Debe verificarse con los archivos reales de la cátedra.
 * Este tipo es el único punto donde se decide: inserción y extracción lo comparten.
 */
public enum BitOrder {
    MSB_FIRST,
    LSB_FIRST;

    public static final BitOrder DEFAULT = MSB_FIRST;

    /** Bit (0 o 1) del byte {@code value} que se transmite en la posición {@code index} (0..7) del byte. */
    public int bitOf(int value, int index) {
        return (value >> shift(index)) & 1;
    }

    /** Devuelve {@code accumulated} con {@code bit} colocado en la posición {@code index} (0..7) transmitida. */
    public int place(int accumulated, int bit, int index) {
        return accumulated | (bit & 1) << shift(index);
    }

    private int shift(int index) {
        if (index < 0 || index > 7) {
            throw new IllegalArgumentException("Índice de bit fuera de rango: " + index);
        }
        return this == MSB_FIRST ? 7 - index : index;
    }
}
