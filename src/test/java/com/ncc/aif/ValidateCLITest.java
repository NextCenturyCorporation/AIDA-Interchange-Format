package com.ncc.aif;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValidateCLITest {
    CommandLine toTest;

    private void expect(String message, ValidateAIFCli.ReturnCode code, String... args) {
        assertEquals(code.ordinal(), toTest.execute(args), message);
        System.out.println();
    }
    private void expectFileError(String message, String... args) {
        expect(message, ValidateAIFCli.ReturnCode.FILE_ERROR, args);
    }
    private void expectFileShouldNotExist(String... args) {
        expect("File should not exist", ValidateAIFCli.ReturnCode.FILE_ERROR, args);
    }

    @BeforeEach
    void createCLI() {
        toTest = new CommandLine(new ValidateAIFCli());
        System.setErr(System.out);
    }

    @Test
    void testSimple() {
        expectFileShouldNotExist("--ldc", "-f", "tmp.ttl");
    }

    @Test
    void requireOntology() {
        expect("CLI requires an ontology option", ValidateAIFCli.ReturnCode.USAGE_ERROR,
                "--nist", "-f", "tmp.ttl");
        expectFileShouldNotExist("--ldc", "-f", "tmp.ttl");
        expectFileShouldNotExist("--program", "-f", "tmp.ttl");
        expectFileShouldNotExist("--ont",
                "src/main/resources/com/ncc/aif/ontologies/SeedlingOntology",
                "src/main/resources/com/ncc/aif/ontologies/LDCOntology",
                "-f", "tmp.ttl");
    }

    @Test
    void abortThreshold() {
        expect("Threshold must be greater than 3", ValidateAIFCli.ReturnCode.USAGE_ERROR,
                "--ldc", "--abort", "-1", "tmp.ttl");
        expectFileShouldNotExist("--ldc", "--abort", "4", "-f", "tmp.ttl");
    }

    @Test
    void threads() {
        expect("Thread count must be at least 1", ValidateAIFCli.ReturnCode.USAGE_ERROR,
                "--ldc", "-t", "-1", "tmp.ttl");
        expectFileShouldNotExist("--ldc", "-t", "4", "-f", "tmp.ttl");
    }

    @Test
    void files() {
        expectFileShouldNotExist("--ldc", "-t", "4", "-f", "tmp.ttl", "another.ttl");
    }
}
