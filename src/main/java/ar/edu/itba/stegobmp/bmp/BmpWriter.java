package ar.edu.itba.stegobmp.bmp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BmpWriter {
    private BmpWriter() {
    }

    public static void write(BmpImage image, Path path) throws IOException {
        Files.write(path, image.toBytes());
    }
}
