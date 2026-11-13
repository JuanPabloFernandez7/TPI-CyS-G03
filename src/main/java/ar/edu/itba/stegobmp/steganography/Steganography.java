package ar.edu.itba.stegobmp.steganography;

/** Algoritmo de esteganografía que opera sobre el cuerpo de un BMP, sin conocer BMP ni el payload. */
public interface Steganography {

    /**
     * Cantidad máxima de bytes para algoritmos que no requieren geometría del portador.
     * Use el overload con {@link CarrierLayout} cuando el algoritmo dependa de la estructura de píxeles.
     */
    long capacityBytes(int bodyLength);

    /**
     * Oculta {@code data} en una copia de {@code body}; {@code body} no se modifica.
     *
     * @throws CapacityExceededException si {@code data} no cabe (no se modifica nada)
     */
    byte[] embed(byte[] body, byte[] data) throws CapacityExceededException;

    /** Lector secuencial sobre {@code body} (no se copia; el llamador no debe modificarlo mientras lo lee). */
    StegoReader newReader(byte[] body);

    /** Capacidad con geometría; algoritmos independientes de la geometría pueden usar la implementación default. */
    default long capacityBytes(int bodyLength, CarrierLayout layout) {
        requireLayout(bodyLength, layout);
        return capacityBytes(bodyLength);
    }

    /** Embedding con geometría; el default conserva el contrato body-only para algoritmos compatibles. */
    default byte[] embed(byte[] body, byte[] data, CarrierLayout layout) throws CapacityExceededException {
        requireLayout(body.length, layout);
        return embed(body, data);
    }

    /** Lector con geometría; el default conserva el contrato body-only para algoritmos compatibles. */
    default StegoReader newReader(byte[] body, CarrierLayout layout) {
        requireLayout(body.length, layout);
        return newReader(body);
    }

    private static void requireLayout(int bodyLength, CarrierLayout layout) {
        if (layout == null) {
            throw new NullPointerException("layout");
        }
        if (layout.bodyLength() != bodyLength) {
            throw new IllegalArgumentException("El layout describe " + layout.bodyLength()
                    + " bytes pero el cuerpo mide " + bodyLength);
        }
    }
}
