package ar.edu.itba.stegobmp.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Compara con fixtures generados por OpenSSL 3.6.2 (ver src/test/resources/crypto/MANIFEST.txt). */
class OpenSslFixtureTest {

    private static final Map<String, String> PASSWORDS = Map.of("oculto", "oculto", "nonascii", "contraseña€ñ", "empty", "");

    static byte[] resource(String name) throws IOException {
        try (InputStream in = OpenSslFixtureTest.class.getResourceAsStream("/crypto/" + name)) {
            assertNotNull(in, "falta el fixture " + name);
            return in.readAllBytes();
        }
    }

    static String fixtureName(CipherSpec spec) {
        String algorithm = switch (spec.algorithm()) {
            case AES_128 -> "aes128";
            case AES_192 -> "aes192";
            case AES_256 -> "aes256";
            case TRIPLE_DES -> "3des";
        };
        return algorithm + "-" + spec.mode().name().toLowerCase() + ".bin";
    }

    static Stream<CipherSpec> specs() {
        return CryptoTestData.allSpecs().stream();
    }

    @ParameterizedTest
    @MethodSource("specs")
    void javaMatchesOpenSslFixtures(CipherSpec spec) throws Exception {
        byte[] plaintext = resource("plaintext-423.bin");
        assertArrayEquals(CryptoTestData.pattern(423), plaintext);
        for (Map.Entry<String, String> entry : PASSWORDS.entrySet()) {
            byte[] fixture = resource(entry.getKey() + "/" + fixtureName(spec));
            long expectedLength = !spec.isPadded() ? 423 : spec.algorithm() == CryptoAlgorithm.TRIPLE_DES ? 424 : 432;
            assertEquals(expectedLength, fixture.length, entry.getKey() + "/" + spec.description());
            try (PasswordPayloadCipher cipher = new PasswordPayloadCipher(spec, entry.getValue().toCharArray())) {
                assertArrayEquals(fixture, cipher.encrypt(plaintext), "cifrado " + entry.getKey() + "/" + spec.description());
                assertArrayEquals(plaintext, cipher.decrypt(fixture), "descifrado " + entry.getKey() + "/" + spec.description());
            }
        }
    }

    @Test
    void manifestHashesMatchTheFixtureFiles() throws Exception {
        String manifest = new String(resource("MANIFEST.txt"), StandardCharsets.UTF_8);
        Map<String, String> hashes = new HashMap<>();
        for (String line : manifest.split("\n")) {
            if (line.matches("[0-9a-f]{64}  \\S+")) {
                hashes.put(line.substring(66), line.substring(0, 64));
            }
        }
        assertEquals(49, hashes.size());
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(resource(entry.getKey()));
            assertEquals(entry.getValue(), CryptoTestData.hex(digest), entry.getKey());
        }
    }
}
