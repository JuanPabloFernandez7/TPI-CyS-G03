package ar.edu.itba.stegobmp.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ArgumentParserTest {

    private static CommandOptions parse(String... args) throws UsageException {
        return ArgumentParser.parse(args);
    }

    private static String error(String... args) {
        return assertThrows(UsageException.class, () -> ArgumentParser.parse(args)).getMessage();
    }

    private static String[] embed(String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "-embed", "-in", "mensaje.txt", "-p", "imagen.bmp", "-out", "salida.bmp", "-steg", "LSB1"));
        args.addAll(List.of(extra));
        return args.toArray(new String[0]);
    }

    private static String[] extract(String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "-extract", "-p", "imagen.bmp", "-out", "mensaje", "-steg", "LSBI"));
        args.addAll(List.of(extra));
        return args.toArray(new String[0]);
    }

    @Test
    void validEmbedWithoutEncryption() throws UsageException {
        CommandOptions o = parse(embed());
        assertEquals(Operation.EMBED, o.operation());
        assertEquals(Path.of("mensaje.txt"), o.input());
        assertEquals(Path.of("imagen.bmp"), o.carrier());
        assertEquals(Path.of("salida.bmp"), o.output());
        assertEquals(StegAlgorithm.LSB1, o.steg());
        assertTrue(o.encryption().isEmpty());
    }

    @Test
    void validExtractWithoutEncryption() throws UsageException {
        CommandOptions o = parse(extract());
        assertEquals(Operation.EXTRACT, o.operation());
        assertNull(o.input());
        assertEquals(Path.of("imagen.bmp"), o.carrier());
        assertEquals(Path.of("mensaje"), o.output());
        assertEquals(StegAlgorithm.LSBI, o.steg());
        assertTrue(o.encryption().isEmpty());
    }

    @Test
    void optionOrderDoesNotMatter() throws UsageException {
        CommandOptions o = parse("-steg", "LSB4", "-out", "o.bmp", "-p", "c.bmp", "-in", "f", "-embed");
        assertEquals(Operation.EMBED, o.operation());
        assertEquals(StegAlgorithm.LSB4, o.steg());
    }

    @Test
    void examplesFromAssignment() throws UsageException {
        CommandOptions e = parse("-embed", "-in", "mensaje1.txt", "-p", "imagen1.bmp", "-out", "imagenmas1.bmp",
                "-steg", "LSBI", "-a", "3des", "-m", "cbc", "-pass", "oculto");
        assertEquals(new EncryptionSettings(CipherAlgorithm.TRIPLE_DES, CipherMode.CBC, "oculto"),
                e.encryption().orElseThrow());
        CommandOptions x = parse("-extract", "-p", "imagenmas1.bmp", "-out", "mensaje1", "-steg", "LSBI",
                "-a", "3des", "-m", "cbc", "-pass", "oculto");
        assertEquals(e.encryption(), x.encryption());
    }

    @Test
    void onlyPasswordDefaultsToAes128Cbc() throws UsageException {
        EncryptionSettings s = parse(embed("-pass", "secreto")).encryption().orElseThrow();
        assertEquals(CipherAlgorithm.AES128, s.algorithm());
        assertEquals(CipherMode.CBC, s.mode());
        assertEquals("secreto", s.password());
    }

    @Test
    void algorithmAndPasswordDefaultsToCbc() throws UsageException {
        EncryptionSettings s = parse(extract("-a", "aes256", "-pass", "x")).encryption().orElseThrow();
        assertEquals(CipherAlgorithm.AES256, s.algorithm());
        assertEquals(CipherMode.CBC, s.mode());
    }

    @Test
    void modeAndPasswordDefaultsToAes128() throws UsageException {
        EncryptionSettings s = parse(embed("-m", "ofb", "-pass", "x")).encryption().orElseThrow();
        assertEquals(CipherAlgorithm.AES128, s.algorithm());
        assertEquals(CipherMode.OFB, s.mode());
    }

    @ParameterizedTest
    @CsvSource({"aes128,AES128", "aes192,AES192", "aes256,AES256", "3des,TRIPLE_DES"})
    void allAlgorithms(String argument, CipherAlgorithm expected) throws UsageException {
        assertEquals(expected, parse(embed("-a", argument, "-pass", "p")).encryption().orElseThrow().algorithm());
    }

    @ParameterizedTest
    @CsvSource({"ecb,ECB", "cbc,CBC", "cfb,CFB", "ofb,OFB"})
    void allModes(String argument, CipherMode expected) throws UsageException {
        assertEquals(expected, parse(embed("-m", argument, "-pass", "p")).encryption().orElseThrow().mode());
    }

    @ParameterizedTest
    @CsvSource({"LSB1,LSB1", "LSB4,LSB4", "LSBI,LSBI"})
    void allStegAlgorithms(String argument, StegAlgorithm expected) throws UsageException {
        assertEquals(expected, parse("-extract", "-p", "a.bmp", "-out", "o", "-steg", argument).steg());
    }

    @Test
    void algorithmOrModeWithoutPasswordMeansNoEncryption() throws UsageException {
        assertTrue(parse(embed("-a", "aes192", "-m", "ecb")).encryption().isEmpty());
        assertTrue(parse(extract("-a", "3des")).encryption().isEmpty());
        assertTrue(parse(extract("-m", "cfb")).encryption().isEmpty());
    }

    @Test
    void passwordMayStartWithDashOrBeEmpty() throws UsageException {
        assertEquals("-embed", parse(embed("-pass", "-embed")).encryption().orElseThrow().password());
        assertEquals("", parse(embed("-pass", "")).encryption().orElseThrow().password());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-a", "-m", "-p", "-in", "-out", "-steg", "-pass", "-extract", "--help", "-", "-x"})
    void passwordStartingWithDashIsAlwaysTheValueOfPass(String password) throws UsageException {
        CommandOptions o = parse(embed("-pass", password));
        assertEquals(password, o.encryption().orElseThrow().password());
        assertEquals(StegAlgorithm.LSB1, o.steg());
        assertEquals(Path.of("imagen.bmp"), o.carrier());
    }

    @Test
    void passwordDashDoesNotSwallowFollowingOptions() throws UsageException {
        CommandOptions o = parse("-pass", "-a", "-a", "3des", "-extract", "-p", "i.bmp", "-out", "o", "-steg", "LSB4");
        EncryptionSettings s = o.encryption().orElseThrow();
        assertEquals("-a", s.password());
        assertEquals(CipherAlgorithm.TRIPLE_DES, s.algorithm());
    }

    @Test
    void optionAtEndWithoutValueIsNotMistakenForValueOfPreviousOption() {
        assertTrue(error(embed("-pass")).contains("Falta el valor de la opción -pass"));
    }

    @Test
    void passwordIsNotExposedInToString() throws UsageException {
        assertFalse(parse(embed("-pass", "ultrasecreto")).toString().contains("ultrasecreto"));
    }

    @Test
    void unknownArgument() {
        assertTrue(error(embed("-foo")).contains("Argumento desconocido: -foo"));
        assertTrue(error(embed("sobrante")).contains("Argumento desconocido: sobrante"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-in", "-p", "-out", "-steg", "-a", "-m", "-pass"})
    void duplicatedValueOption(String option) {
        String message = error(embed("-a", "aes128", "-m", "cbc", "-pass", "x", option, "otro"));
        assertTrue(message.contains("Opción duplicada: " + option), message);
    }

    @Test
    void duplicatedOperation() {
        assertTrue(error(embed("-embed")).contains("Opción duplicada: -embed"));
        assertTrue(error(extract("-extract")).contains("Opción duplicada: -extract"));
    }

    @Test
    void bothOperationsAreInvalid() {
        assertTrue(error(embed("-extract")).contains("excluyentes"));
    }

    @Test
    void missingOperation() {
        assertTrue(error().contains("-embed o -extract"));
        assertTrue(error("-p", "a.bmp", "-out", "o", "-steg", "LSB1").contains("-embed o -extract"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-in", "-p", "-out", "-steg", "-a", "-m", "-pass"})
    void missingValueAtEnd(String option) {
        String[] args = {"-embed", "-in", "f", "-p", "a.bmp", "-out", "o.bmp", "-steg", "LSB1"};
        if (!List.of("-in", "-p", "-out", "-steg").contains(option)) {
            assertTrue(error(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], option)
                    .contains("Falta el valor de la opción " + option));
        } else {
            String message = error("-embed", option);
            assertTrue(message.contains("Falta el valor de la opción " + option), message);
        }
    }

    @Test
    void missingRequiredOptionsAreAllReported() {
        String message = error("-embed", "-p", "a.bmp");
        assertTrue(message.contains("-in") && message.contains("-out") && message.contains("-steg"), message);
        assertTrue(error("-extract", "-out", "o", "-steg", "LSB1").contains("-p"));
    }

    @Test
    void inputNotValidForExtract() {
        assertTrue(error(extract("-in", "x")).contains("-in no es válida con -extract"));
    }

    @Test
    void embedRequiresInput() {
        assertTrue(error("-embed", "-p", "a.bmp", "-out", "o.bmp", "-steg", "LSB1").contains("-in"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lsb1", "LSB2", "LSBE", ""})
    void invalidSteg(String value) {
        assertTrue(error("-extract", "-p", "a.bmp", "-out", "o", "-steg", value).contains("-steg"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AES128", "aes64", "des", "3DES", ""})
    void invalidAlgorithm(String value) {
        assertTrue(error(embed("-a", value, "-pass", "x")).contains("-a"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CBC", "ctr", "gcm", ""})
    void invalidMode(String value) {
        assertTrue(error(embed("-m", value, "-pass", "x")).contains("-m"));
    }

    @Test
    void invalidAlgorithmIsReportedEvenWithoutPassword() {
        assertTrue(error(embed("-a", "rc4")).contains("-a"));
    }

    @Test
    void helpRequest() {
        assertTrue(ArgumentParser.isHelpRequest(new String[] {"-h"}));
        assertTrue(ArgumentParser.isHelpRequest(new String[] {"--help"}));
        assertFalse(ArgumentParser.isHelpRequest(new String[] {}));
        assertFalse(ArgumentParser.isHelpRequest(new String[] {"-h", "-embed"}));
    }
}
