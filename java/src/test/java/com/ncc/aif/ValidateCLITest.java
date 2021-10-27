package com.ncc.aif;

import com.google.common.io.Resources;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValidateCLITest {
    private static final boolean SHOW_OUTPUT = false;
    private PrintStream oldOut;
    private PrintStream oldErr;
    private ByteArrayOutputStream baos;

    private void expect(String shouldContain, ValidateAIFCli.ReturnCode code, String... args) {
        int result = ValidateAIFCli.execute(args);
        if (SHOW_OUTPUT) {
            printOutput(args);
        }
        assertEquals(code.ordinal(), result, "Wrong error code. Should have returned " + code.ordinal());
        assertTrue(baos.toString().contains(shouldContain), "Output does not contain required string: " + shouldContain);
    }
    private void expectUsageError(String shouldContain, String... args) {
        expect(shouldContain, ValidateAIFCli.ReturnCode.USAGE_ERROR, args);
    }
    private void expectCorrect(String... args) {
        expect(ValidateAIFCli.START_MSG, ValidateAIFCli.ReturnCode.FILE_ERROR, args);
    }
    private void printOutput(String... args) {
        StringBuilder builder = new StringBuilder("Args: ");
        for (String arg : args) {
            builder.append(arg).append(" ");
        }
        builder.setLength(builder.length() - 1);
        oldOut.println(builder.toString());
        oldOut.println(baos.toString());
    }

    @BeforeAll
    void setup() {
        oldOut = System.out;
        oldErr = System.err;
    }

    @BeforeEach
    void createCLI() {
        baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        System.setOut(out);
        System.setErr(out);
    }

    @Test
    void version() throws IOException {
        Properties props = new Properties();
        props.load(Resources.getResource(ValidateAIFCli.VERSION_FILE).openStream());
        expectUsageError(props.getProperty(ValidateAIFCli.VERSION_PROPERTY), "-v");
    }

    @Nested
    class OntologyArguments {
        @Test
        void missingOntology() {
            expectUsageError(ValidateAIFCli.ERR_MISSING_ONT_FLAG,"--nist", "-f", "tmp.ttl");
        }
        @Test
        void tooManyOntologies() {
            expectUsageError(ValidateAIFCli.ERR_TOO_MANY_ONT_FLAGS,"--ldc", "--program", "--nist", "-f", "tmp.ttl");
        }
        @Test
        void correctLDC() {
            expectCorrect("--ldc", "-f", "tmp.ttl");
        }
        @Test
        void correctProgram() {
            expectCorrect("--program", "-f", "tmp.ttl");
        }
        @Test
        void correctCustom() {
            expectCorrect("--ont",
                    "src/main/resources/com/ncc/aif/ontologies/SeedlingOntology",
                    "src/main/resources/com/ncc/aif/ontologies/LDCOntology",
                    "-f", "tmp.ttl");
        }
        @Test
        void correctDWD() {
            expectCorrect("--dwd", "-f", "tmp.ttl");
        }
    }

    @Nested
    class AbortArgument {
        @Test
        void thresholdBadType() {
            expectUsageError(ValidateAIFCli.ERR_BAD_ARGTYPE.replaceAll("%.", ""),
                    "--ldc", "--abort", "foobar", "-f", "tmp.ttl");
        }
        @Test
        void thresholdTooLow() {
            expectUsageError(ValidateAIFCli.ERR_SMALLER_THAN_MIN.replaceAll("%.", ""),
                    "--ldc", "--abort", "1", "-f", "tmp.ttl");
        }
        @Test
        void correctThreshold() {
            expectCorrect("--ldc", "--abort", "4", "-f", "tmp.ttl");
        }
        @Test
        void correctThresholdWithoutValue() {
            expectCorrect("--ldc", "--abort", "-f", "tmp.ttl");
        }
    }

    @Nested
    class DepthArgument {
        @Test
        void depthBadType() {
            expectUsageError(ValidateAIFCli.ERR_BAD_ARGTYPE.replaceAll("%.", ""),
                    "--ldc", "--depth", "foobar", "-t", "2", "-f", "tmp.ttl");
        }
        @Test
        void depthTooLow() {
            expectUsageError(ValidateAIFCli.ERR_SMALLER_THAN_MIN.replaceAll("%.", ""),
                    "--ldc", "--depth", "-1", "-t=2", "-f", "tmp.ttl");
        }
        @Test
        void correctDepth() {
            expectCorrect("--ldc", "--depth", "1", "-t=2", "-f", "tmp.ttl");
        }
        @Test
        void requiresMultithreads() {
            expectUsageError(ValidateAIFCli.ERR_DEPTH_REQUIRES_T, "--ldc", "--depth", "-f", "tmp.ttl");
        }
        @Test
        void correctDepthWithoutValue() {
            expectCorrect("--ldc", "--depth", "-t=2", "-f", "tmp.ttl");
        }
    }

    @Nested
    class HypothesisMaxSizeArgument {
        @Test
        void hypothesisMaxSizeBadType() {
            expectUsageError(ValidateAIFCli.ERR_BAD_ARGTYPE.replaceAll("%.", ""),
                    "--ldc", "--hypothesis-max-size", "s", "-t", "2", "-f", "tmp.ttl");
        }
        @Test
        void hypothesisMaxSizeNegative() {
            expectUsageError(ValidateAIFCli.ERR_BAD_ARGTYPE.replaceAll("%.", ""),
                    "--ldc", "--hypothesis-max-size", "-10", "2", "-f", "tmp.ttl");
        }
        @Test
        void hypothesisMaxSizeValid() {
            expectCorrect("--ldc", "--hypothesis-max-size", "10", "-t=2", "-f", "tmp.ttl");
        }
    }

    @Nested
    class ThreadArgument {
        @Test
        void threadsTooLow() {
            expectUsageError(ValidateAIFCli.ERR_SMALLER_THAN_MIN.replaceAll("%.", ""),
                    "--ldc", "-t", "-1", "-f", "tmp.ttl");
        }
        @Test
        void correctThread() {
            expectCorrect("--ldc", "-t", "4", "-f", "tmp.ttl");
        }

    }

    @Nested
    class FileArguments {
        @Test
        void correctMultipleFiles() {
            expectCorrect("--ldc", "-t", "4", "-f", "tmp.ttl", "another.ttl");
        }
        @Test
        void correctDirectory() {
            expectCorrect("--ldc", "-t", "4", "-d", "tmp");
        }
        @Test
        void tooManyFileArguments() {
            expectUsageError(ValidateAIFCli.ERR_TOO_MANY_FILE_FLAGS,"--ldc", "-d", "tmp", "-f", "tmp.ttl");
        }
        @Test
        void missingFileArguments() {
            expectUsageError(ValidateAIFCli.ERR_MISSING_FILE_FLAG,"--ldc");
        }
        @Test
        void missingSpecifiedFile() {
            expectUsageError("Expected parameter for option","-f", "--ldc");
        }
        @Disabled("Bug in picocli where this is valid. Not using assertNoMissingMandatoryParameter")
        @Test
        void missingSpecifiedDirectory() {
            expectUsageError(ValidateAIFCli.ERR_MISSING_FILE_FLAG,"-d", "--ldc", "--program");
        }
    }

    @Nested
    class CombinedArguments {
        @Test
        void correctCombinedArguments() {
            expectCorrect("--ldc", "-op", "-f", "tmp.ttl");
        }
    }
}
