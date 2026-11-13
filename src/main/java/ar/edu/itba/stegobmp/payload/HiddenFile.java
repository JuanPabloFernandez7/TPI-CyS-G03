package ar.edu.itba.stegobmp.payload;

import java.util.Arrays;
import java.util.Objects;

/** Archivo a ocultar: bytes completos (sin interpretar) y su extensión. Inmutable. */
public final class HiddenFile {
    private final byte[] data;
    private final FileExtension extension;

    public HiddenFile(byte[] data, FileExtension extension) {
        this.data = Objects.requireNonNull(data, "data").clone();
        this.extension = Objects.requireNonNull(extension, "extension");
    }

    public long dataLength() {
        return data.length;
    }

    public byte[] copyData() {
        return data.clone();
    }

    public FileExtension extension() {
        return extension;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HiddenFile other
                && Arrays.equals(data, other.data)
                && extension.equals(other.extension);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(data) + extension.hashCode();
    }

    @Override
    public String toString() {
        return "HiddenFile[" + data.length + " bytes, " + extension + "]";
    }
}
