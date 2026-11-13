package ar.edu.itba.stegobmp;

import ar.edu.itba.stegobmp.app.Embedder;
import ar.edu.itba.stegobmp.app.Extractor;
import ar.edu.itba.stegobmp.app.OperationException;
import ar.edu.itba.stegobmp.app.Steganographies;
import ar.edu.itba.stegobmp.cli.ArgumentParser;
import ar.edu.itba.stegobmp.cli.CommandOptions;
import ar.edu.itba.stegobmp.cli.UsageException;
import ar.edu.itba.stegobmp.cli.Operation;
import ar.edu.itba.stegobmp.steganography.Steganography;
import java.io.PrintStream;
import java.util.Optional;

public final class Main {
    public static final int EXIT_OK = 0;
    public static final int EXIT_FAILURE = 1;
    public static final int EXIT_USAGE_ERROR = 2;
    public static final int EXIT_NOT_IMPLEMENTED = 3;

    static final String USAGE = """
            Uso:
              stegobmp -embed -in <archivo> -p <imagen.bmp> -out <resultado.bmp> -steg <LSB1|LSB4|LSBI> [-a <alg>] [-m <modo>] [-pass <password>]
              stegobmp -extract -p <imagen.bmp> -out <archivo> -steg <LSB1|LSB4|LSBI> [-a <alg>] [-m <modo>] [-pass <password>]
              stegobmp -h | --help

            Opciones:
              -embed          Oculta un archivo en un BMP
              -extract        Extrae un archivo oculto de un BMP
              -in <archivo>   Archivo a ocultar (solo -embed)
              -p <bmp>        BMP portador
              -out <archivo>  BMP de salida (-embed) o archivo extraído (-extract)
              -steg <alg>     Algoritmo: LSB1, LSB4 o LSBI
              -a <alg>        Cifrado: aes128, aes192, aes256 o 3des
              -m <modo>       Modo: ecb, cfb, ofb o cbc
              -pass <pass>    Password de cifrado

            Cifrado (solo si se indica -pass; sin -pass no se cifra):
              sin -a: aes128 | sin -m: cbc
            """;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (ArgumentParser.isHelpRequest(args)) {
            out.print(USAGE);
            return EXIT_OK;
        }
        CommandOptions options;
        try {
            options = ArgumentParser.parse(args);
        } catch (UsageException e) {
            err.println("Error: " + e.getMessage());
            err.println();
            err.print(USAGE);
            return EXIT_USAGE_ERROR;
        }
        Optional<Steganography> steganography = Steganographies.forAlgorithm(options.steg());
        if (steganography.isEmpty()) {
            err.println("Argumentos válidos (" + options.operation() + " con " + options.steg()
                    + "), pero la operación con ese algoritmo todavía no está implementada.");
            return EXIT_NOT_IMPLEMENTED;
        }
        try {
            if (options.operation() == Operation.EMBED) {
                new Embedder(steganography.get()).embed(options);
            } else {
                new Extractor(steganography.get()).extract(options);
            }
        } catch (OperationException e) {
            err.println("Error: " + e.getMessage());
            return EXIT_FAILURE;
        }
        return EXIT_OK;
    }
}
