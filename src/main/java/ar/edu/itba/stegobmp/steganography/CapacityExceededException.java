package ar.edu.itba.stegobmp.steganography;

/** Los datos a ocultar no caben en el portador. */
public final class CapacityExceededException extends Exception {
    private final long requiredBytes;
    private final long capacityBytes;

    public CapacityExceededException(long requiredBytes, long capacityBytes) {
        super("El portador no puede albergar los datos: se requieren " + requiredBytes
                + " bytes (incluyendo tamaño y extensión) y la capacidad máxima del BMP es "
                + capacityBytes + " bytes");
        this.requiredBytes = requiredBytes;
        this.capacityBytes = capacityBytes;
    }

    public long requiredBytes() {
        return requiredBytes;
    }

    public long capacityBytes() {
        return capacityBytes;
    }
}
