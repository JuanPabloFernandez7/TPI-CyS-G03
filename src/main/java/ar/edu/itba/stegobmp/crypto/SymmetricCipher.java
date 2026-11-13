package ar.edu.itba.stegobmp.crypto;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Único código que usa JCE; igual para AES y 3DES. Sin estado: cada operación crea su propio {@link Cipher}.
 * Solo SunJCE (el proveedor por defecto) y siempre con la transformación completa algoritmo/modo/padding.
 */
public final class SymmetricCipher {

    private SymmetricCipher() {
    }

    public static byte[] encrypt(CipherSpec spec, DerivedKey key, byte[] plaintext) throws CryptoException {
        requireArguments(spec, key, plaintext, "plaintext");
        Cipher cipher = initialized(spec, key, Cipher.ENCRYPT_MODE, true);
        try {
            return nonNull(cipher.doFinal(plaintext));
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new EncryptionException("Error al cifrar con " + spec.transformation() + ".", e);
        }
    }

    public static byte[] decrypt(CipherSpec spec, DerivedKey key, byte[] ciphertext) throws CryptoException {
        requireArguments(spec, key, ciphertext, "ciphertext");
        if (spec.isPadded()) {
            requireBlockAligned(spec, ciphertext.length);
        }
        Cipher cipher = initialized(spec, key, Cipher.DECRYPT_MODE, false);
        try {
            return nonNull(cipher.doFinal(ciphertext));
        } catch (BadPaddingException e) {
            throw new InvalidPaddingException("Relleno inválido al descifrar con " + spec.description()
                    + ": la contraseña, el algoritmo o el modo pueden ser incorrectos, o los datos estar dañados.", e);
        } catch (IllegalBlockSizeException e) {
            throw new InvalidCiphertextException("El ciphertext no es compatible con " + spec.description() + ".", e);
        } catch (RuntimeException e) {
            throw new DecryptionException("Error al descifrar con " + spec.transformation() + ".", e);
        }
    }

    private static void requireArguments(CipherSpec spec, DerivedKey key, byte[] data, String dataName)
            throws InvalidCryptoParametersException {
        if (spec == null || key == null || data == null) {
            throw new InvalidCryptoParametersException(
                    "La especificación, la clave y el " + dataName + " no pueden ser nulos.");
        }
        if (key.keyLength() != spec.algorithm().keyBytes()) {
            throw new InvalidCryptoParametersException("La clave mide " + key.keyLength() + " bytes y "
                    + spec.algorithm().displayName() + " requiere " + spec.algorithm().keyBytes() + ".");
        }
        if (key.ivLength() != spec.ivBytes()) {
            throw new InvalidCryptoParametersException("El IV mide " + key.ivLength() + " bytes y "
                    + spec.description() + " requiere " + spec.ivBytes() + ".");
        }
    }

    private static void requireBlockAligned(CipherSpec spec, int length) throws InvalidCiphertextException {
        if (length == 0) {
            throw new InvalidCiphertextException("El ciphertext está vacío y " + spec.description()
                    + " con padding siempre produce al menos un bloque.");
        }
        if (length % spec.blockBytes() != 0) {
            throw new InvalidCiphertextException("El ciphertext mide " + length
                    + " bytes y no es múltiplo del tamaño de bloque (" + spec.blockBytes() + ") de " + spec.description() + ".");
        }
    }

    private static Cipher initialized(CipherSpec spec, DerivedKey key, int opMode, boolean encrypting)
            throws CryptoException {
        byte[] keyCopy = key.keyBytes();
        byte[] ivCopy = key.ivBytes();
        try {
            Cipher cipher = Cipher.getInstance(spec.transformation());
            SecretKeySpec keySpec = new SecretKeySpec(keyCopy, spec.algorithm().jcaName());
            if (spec.ivBytes() == 0) {
                cipher.init(opMode, keySpec);
            } else {
                cipher.init(opMode, keySpec, new IvParameterSpec(ivCopy));
            }
            return cipher;
        } catch (java.security.NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException e) {
            throw new InvalidCryptoParametersException(
                    "La combinación " + spec.transformation() + " no está soportada en este entorno.", e);
        } catch (GeneralSecurityException e) {
            String message = "No se pudo inicializar " + spec.transformation() + ".";
            throw encrypting ? new EncryptionException(message, e) : new DecryptionException(message, e);
        } finally {
            Arrays.fill(keyCopy, (byte) 0);
            Arrays.fill(ivCopy, (byte) 0);
        }
    }

    private static byte[] nonNull(byte[] result) {
        return result == null ? new byte[0] : result;
    }
}
