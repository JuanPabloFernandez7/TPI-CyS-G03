package ar.edu.itba.stegobmp.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.security.auth.DestroyFailedException;

/**
 * PBKDF2-HMAC-SHA256, 10.000 iteraciones y salt fijo de 8 bytes en cero (consigna §5.4). Hace una sola
 * derivación de {@code keyBytes + ivBytes} y la reparte: clave primero, IV después (convención de OpenSSL, A3).
 * La contraseña se codifica en UTF-8 sin normalizar (A4).
 */
public final class Pbkdf2KeyDerivation implements KeyDerivation {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int ITERATIONS = 10_000;
    public static final int SALT_BYTES = 8;

    @Override
    public DerivedKey derive(char[] password, CipherSpec spec) throws CryptoException {
        if (password == null) {
            throw new InvalidCryptoParametersException("La contraseña no puede ser nula.");
        }
        if (spec == null) {
            throw new InvalidCryptoParametersException("La especificación de cifrado no puede ser nula.");
        }
        requireEncodable(password);

        int keyBytes = spec.algorithm().keyBytes();
        int ivBytes = spec.ivBytes();
        int total = keyBytes + ivBytes;
        PBEKeySpec pbeSpec = new PBEKeySpec(password, new byte[SALT_BYTES], ITERATIONS, total * 8);
        byte[] derived = null;
        byte[] key = null;
        byte[] iv = null;
        try {
            SecretKey secret = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(pbeSpec);
            try {
                derived = secret.getEncoded();
            } finally {
                try {
                    secret.destroy();
                } catch (DestroyFailedException ignored) {
                    // el JDK no siempre permite destruir la clave; ya se borran las copias propias
                }
            }
            if (derived == null || derived.length != total) {
                throw new KeyDerivationException("PBKDF2 devolvió una cantidad inesperada de bytes.", null);
            }
            key = Arrays.copyOfRange(derived, 0, keyBytes);
            iv = Arrays.copyOfRange(derived, keyBytes, total);
            return DerivedKey.of(key, iv);
        } catch (NoSuchAlgorithmException e) {
            throw new KeyDerivationException("No se pudo derivar la clave: " + ALGORITHM + " no está disponible en este entorno.", e);
        } catch (InvalidKeySpecException e) {
            throw new KeyDerivationException("No se pudo derivar la clave: parámetros rechazados por " + ALGORITHM + ".", e);
        } finally {
            pbeSpec.clearPassword();
            zero(derived);
            zero(key);
            zero(iv);
        }
    }

    private static void requireEncodable(char[] password) throws InvalidCryptoParametersException {
        for (int i = 0; i < password.length; i++) {
            char c = password[i];
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < password.length && Character.isLowSurrogate(password[i + 1])) {
                    i++;
                    continue;
                }
                throw unencodable();
            }
            if (Character.isLowSurrogate(c)) {
                throw unencodable();
            }
        }
    }

    private static InvalidCryptoParametersException unencodable() {
        return new InvalidCryptoParametersException(
                "Parámetros de cifrado inválidos: la contraseña contiene caracteres que no se pueden codificar en UTF-8.");
    }

    private static void zero(byte[] array) {
        if (array != null) {
            Arrays.fill(array, (byte) 0);
        }
    }
}
