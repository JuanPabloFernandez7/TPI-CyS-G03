package ar.edu.itba.stegobmp.crypto;

/**
 * Cifra y descifra los bytes del payload. No conoce su estructura ni el transporte esteganográfico.
 * No autentica: un descifrado exitoso no prueba que la contraseña, el algoritmo o el modo sean los correctos.
 */
public interface PayloadCipher {

    byte[] encrypt(byte[] plaintext) throws CryptoException;

    byte[] decrypt(byte[] ciphertext) throws CryptoException;
}
