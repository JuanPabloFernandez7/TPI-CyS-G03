package ar.edu.itba.stegobmp.app;

import ar.edu.itba.stegobmp.bmp.BmpFormatException;
import ar.edu.itba.stegobmp.bmp.BmpImage;
import ar.edu.itba.stegobmp.bmp.BmpReader;
import ar.edu.itba.stegobmp.bmp.BmpWriter;
import ar.edu.itba.stegobmp.cli.CommandOptions;
import ar.edu.itba.stegobmp.cli.EncryptionSettings;
import ar.edu.itba.stegobmp.crypto.CryptoException;
import ar.edu.itba.stegobmp.crypto.PasswordPayloadCipher;
import ar.edu.itba.stegobmp.payload.FileExtension;
import ar.edu.itba.stegobmp.payload.HiddenFile;
import ar.edu.itba.stegobmp.payload.PayloadCodec;
import ar.edu.itba.stegobmp.payload.PayloadFormatException;
import ar.edu.itba.stegobmp.steganography.CapacityExceededException;
import ar.edu.itba.stegobmp.steganography.CarrierLayout;
import ar.edu.itba.stegobmp.steganography.Steganography;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;

/**
 * EMBED: serializa el archivo, lo cifra y enmarca si corresponde, y lo oculta en el BMP.
 * Calcula y valida la capacidad antes de cifrar y no escribe nada si algo falla.
 */
public final class Embedder {
    private final Steganography steganography;

    public Embedder(Steganography steganography) {
        this.steganography = Objects.requireNonNull(steganography);
    }

    public void embed(CommandOptions options) throws OperationException {
        byte[] content = readInput(options);
        BmpImage image = readCarrier(options);

        HiddenFile file;
        try {
            file = new HiddenFile(content, FileExtension.fromFileName(options.input().getFileName().toString()));
        } catch (PayloadFormatException e) {
            throw new OperationException("No se puede ocultar \"" + options.input() + "\": " + e.getMessage(), e);
        }

        byte[] stream = buildStream(options, file, image);
        byte[] newBody;
        try {
            newBody = steganography.embed(image.copyBody(), stream, layout(image));
        } catch (CapacityExceededException e) {
            throw capacityError(e.requiredBytes(), e.capacityBytes());
        }
        try {
            BmpWriter.write(image.withBody(newBody), options.output());
        } catch (IOException e) {
            throw new OperationException("No se pudo escribir \"" + options.output() + "\": " + e.getMessage(), e);
        }
    }

    private byte[] buildStream(CommandOptions options, HiddenFile file, BmpImage image) throws OperationException {
        long capacity = steganography.capacityBytes(image.bodyLength(), layout(image));
        try {
            long plainLength = PayloadCodec.plainStreamLength(file);
            if (options.encryption().isEmpty()) {
                requireCapacity(plainLength, capacity);
                return PayloadCodec.serializeFile(file);
            }
            EncryptionSettings settings = options.encryption().get();
            char[] password = settings.password().toCharArray();
            try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(CipherSpecs.from(settings), password)) {
                requireCapacity(PayloadCodec.framedLength(cipher.ciphertextLength(plainLength)), capacity);
                byte[] plaintext = PayloadCodec.serializeFile(file);
                try {
                    return PayloadCodec.frameCiphertext(cipher.encrypt(plaintext));
                } finally {
                    Arrays.fill(plaintext, (byte) 0);
                }
            } finally {
                Arrays.fill(password, '\0');
            }
        } catch (PayloadFormatException e) {
            throw new OperationException("No se puede ocultar el archivo: " + e.getMessage(), e);
        } catch (CryptoException e) {
            throw new OperationException("No se pudo cifrar el archivo: " + e.getMessage(), e);
        }
    }

    private static CarrierLayout layout(BmpImage image) {
        return new CarrierLayout(image.header().width(), image.header().rowCount(), image.header().stride());
    }

    private static void requireCapacity(long required, long capacity) throws OperationException {
        if (required > capacity) {
            throw capacityError(required, capacity);
        }
    }

    private static OperationException capacityError(long required, long capacity) {
        return new OperationException("El portador no tiene capacidad suficiente: se necesitan " + required
                + " bytes y la capacidad máxima es " + capacity + " bytes.");
    }

    private static byte[] readInput(CommandOptions options) throws OperationException {
        try {
            return Files.readAllBytes(options.input());
        } catch (IOException e) {
            throw new OperationException("No se pudo leer el archivo a ocultar \"" + options.input() + "\": "
                    + describe(e), e);
        }
    }

    private static BmpImage readCarrier(CommandOptions options) throws OperationException {
        try {
            return BmpReader.read(options.carrier());
        } catch (BmpFormatException e) {
            throw new OperationException("El portador \"" + options.carrier() + "\" no es un BMP válido: "
                    + e.getMessage(), e);
        } catch (IOException e) {
            throw new OperationException("No se pudo leer el portador \"" + options.carrier() + "\": "
                    + describe(e), e);
        }
    }

    static String describe(IOException e) {
        return e instanceof java.nio.file.NoSuchFileException ? "el archivo no existe" : String.valueOf(e.getMessage());
    }
}
