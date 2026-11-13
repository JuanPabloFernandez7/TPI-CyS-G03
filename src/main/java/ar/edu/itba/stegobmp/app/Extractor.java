package ar.edu.itba.stegobmp.app;

import ar.edu.itba.stegobmp.bmp.BmpFormatException;
import ar.edu.itba.stegobmp.bmp.BmpImage;
import ar.edu.itba.stegobmp.bmp.BmpReader;
import ar.edu.itba.stegobmp.cli.CommandOptions;
import ar.edu.itba.stegobmp.cli.EncryptionSettings;
import ar.edu.itba.stegobmp.crypto.CryptoException;
import ar.edu.itba.stegobmp.crypto.InvalidCiphertextException;
import ar.edu.itba.stegobmp.crypto.InvalidPaddingException;
import ar.edu.itba.stegobmp.crypto.PasswordPayloadCipher;
import ar.edu.itba.stegobmp.payload.ByteSource;
import ar.edu.itba.stegobmp.payload.HiddenFile;
import ar.edu.itba.stegobmp.payload.PayloadCodec;
import ar.edu.itba.stegobmp.payload.PayloadFormatException;
import ar.edu.itba.stegobmp.steganography.CarrierLayout;
import ar.edu.itba.stegobmp.steganography.Steganography;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * EXTRACT: recupera el payload del BMP, lo descifra si corresponde y escribe el archivo en {@code -out + extensión}.
 * El archivo de salida se escribe solo después de validar todo el payload.
 */
public final class Extractor {
    private static final String WRONG_KEY_HINT = " Verifique la contraseña, el algoritmo y el modo; también puede "
            + "ocurrir que la imagen no contenga datos ocultos con ese algoritmo.";

    private final Steganography steganography;

    public Extractor(Steganography steganography) {
        this.steganography = Objects.requireNonNull(steganography);
    }

    /** Devuelve la ruta del archivo escrito. */
    public Path extract(CommandOptions options) throws OperationException {
        BmpImage image = readCarrier(options);
        ByteSource source = new StegoReaderSource(
                steganography.newReader(image.copyBody(), layout(image)));
        HiddenFile file = options.encryption().isPresent()
                ? readEncrypted(source, options.encryption().get())
                : readPlain(source);

        Path target = Path.of(file.extension().appendTo(options.output().toString()));
        try {
            Files.write(target, file.copyData());
        } catch (IOException e) {
            throw new OperationException("No se pudo escribir \"" + target + "\": " + e.getMessage(), e);
        }

        return target;
    }

    private static CarrierLayout layout(BmpImage image) {
        return new CarrierLayout(image.header().width(), image.header().rowCount(), image.header().stride());
    }

    private static HiddenFile readPlain(ByteSource source) throws OperationException {
        try {
            return PayloadCodec.readFile(source);
        } catch (PayloadFormatException e) {
            throw new OperationException("El payload oculto es inválido o está corrupto: " + e.getMessage()
                    + " Si el archivo fue ocultado con cifrado, indique -pass (y -a / -m si no son los valores por defecto).", e);
        }
    }

    private static HiddenFile readEncrypted(ByteSource source, EncryptionSettings settings) throws OperationException {
        byte[] ciphertext;
        try {
            ciphertext = PayloadCodec.readCiphertextBlock(source);
        } catch (PayloadFormatException e) {
            throw new OperationException("El payload cifrado es inválido o está corrupto: " + e.getMessage()
                    + WRONG_KEY_HINT, e);
        }
        char[] password = settings.password().toCharArray();
        byte[] plaintext = null;
        try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(CipherSpecs.from(settings), password)) {
            plaintext = cipher.decrypt(ciphertext);
            return PayloadCodec.parseFile(plaintext);
        } catch (InvalidPaddingException | InvalidCiphertextException e) {
            throw new OperationException("No se pudo descifrar el contenido." + WRONG_KEY_HINT, e);
        } catch (CryptoException e) {
            throw new OperationException("No se pudo descifrar el contenido: " + e.getMessage(), e);
        } catch (PayloadFormatException e) {
            throw new OperationException("El contenido descifrado no tiene un formato válido (" + e.getMessage()
                    + "). Con CFB y OFB una contraseña incorrecta no se detecta al descifrar." + WRONG_KEY_HINT, e);
        } finally {
            Arrays.fill(password, '\0');
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
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
                    + Embedder.describe(e), e);
        }
    }
}
