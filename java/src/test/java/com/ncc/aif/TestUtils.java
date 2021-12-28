package com.ncc.aif;

import static com.ncc.aif.AIFUtils.addStandardNamespaces;
import static com.ncc.aif.AIFUtils.makeEntity;
import static com.ncc.aif.AIFUtils.makeEvent;
import static com.ncc.aif.AIFUtils.makeHypothesis;
import static com.ncc.aif.AIFUtils.makeRelation;
import static com.ncc.aif.AIFUtils.makeSystemWithURI;
import static com.ncc.aif.AIFUtils.makeTextJustification;
import static com.ncc.aif.AIFUtils.markAsArgument;
import static com.ncc.aif.AIFUtils.markType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.ncc.aif.util.AIFOrderedTurtleWriter;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.topbraid.shacl.vocabulary.SH;

import ch.qos.logback.classic.Logger;

/**
 * Utilities for testing AIF functionality and/or creating examples.
 * Features:
 * <p><ul>
 * <li>Contains methods for creating a valid AIF entity, event, relation, and hypothesis objects.;</li>
 * <li>Objects, assertions, and cluster URIs are numbered in a standard, sequential, simple way;</li>
 * <li>Is entirely ontology-agnostic;</li>
 * <li>Contains methods for asserting test model validity and dumping models.</li>
 * </ul></p>
 * Call {@link #startNewTest()} before each test to ensure a clean model.
 */
class TestUtils {
    protected Logger logger;

    // Constructor parameters
    private final ValidateAIF validator;
    private final String annotationNamespace;
    private final boolean dumpAlways;
    private final boolean dumpToFile;
    private final AIFOrderedTurtleWriter writer;

    private static final String DUMP_DIRECTORY = "test-dump-output";

    // Counters for the various elements tracked by the TestUtils
    private int assertionCount;
    private int entityCount;
    private int eventCount;
    private int relationCount;
    private int hypothesisCount;
    private int clusterCount;
    private int documentCount;

    // Data created by each test
    protected Model model;
    protected Resource system;
    private Map<Triple<Resource, Resource, Resource>, Integer> expectedCounts;

    /**
     * Constructor for utilities for testing AIF functionality.
     *
     * @param annotationNamespace namespace to use with URIs
     * @param validator           an AIF validator instantiated based on the caller's ontology
     * @param dumpAlways          whether or not to force dumping of models after validation
     * @param dumpToFile          dump to file or stdout
     */
    TestUtils(String annotationNamespace, ValidateAIF validator, boolean dumpAlways, boolean dumpToFile) {
        this.annotationNamespace = annotationNamespace;
        this.validator = validator;
        this.dumpAlways = dumpAlways;
        this.dumpToFile = dumpToFile;
        this.logger = (Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        this.expectedCounts = new HashMap<>();
        writer = new AIFOrderedTurtleWriter();
    }

    /**
     * Call before each test.  Returns a new, empty model with standard AIF namespaces.
     *
     * @return a new model with which to start a test; caller may wish to add prefixes for the ontology and annotation
     */
    Model startNewTest() {
        if (model != null) {
            model.close();
        }
        model = ModelFactory.createDefaultModel();
        // adding namespace prefixes makes the Turtle output more readable
        addStandardNamespaces(model);
        // every AIF needs an object for the system responsible for creating it
        system = makeSystemWithURI(model, getTestSystemUri());
        assertionCount = entityCount = eventCount = relationCount = hypothesisCount = clusterCount = documentCount = 1;

        expectedCounts.clear();

        return model;
    }

    /**
     * Returns a prefixed String system URI in case tests need to create their own models.
     */
    String getTestSystemUri() {
        return getUri("testSystem");
    }

    /**
     * Returns a prefixed String URI of the specified name.
     */
    String getUri(String localName) {
        return annotationNamespace + localName;
    }

    /**
     * Returns a unique prefixed String URI for use with assertions.
     */
    String getAssertionUri() {
        return getUri("assertion-" + assertionCount++);
    }

    /**
     * Returns a unique prefixed String URI for use with entities.
     */
    String getEntityUri() {
        return getUri("entity-" + entityCount++);
    }

    /**
     * Returns a unique prefixed String URI for use with events.
     */
    String getEventUri() {
        return getUri("event-" + eventCount++);
    }

    /**
     * Returns a unique prefixed String URI for use with relations.
     */
    String getRelationUri() {
        return getUri("relation-" + relationCount++);
    }

    /**
     * Returns a unique prefixed String URI for use with hypotheses.
     */
    String getHypothesisUri() {
        return getUri("hypothesis-" + hypothesisCount++);
    }

    /**
     * Returns a unique prefixed String URI for use with clusters.
     */
    String getClusterUri() {
        return getUri("cluster-" + clusterCount++);
    }

    /**
     * Returns the test system created by {@link #startNewTest()}.
     */
    Resource getSystem() {
        return system;
    }

    /**
     * Returns a unique document name.
     */
    String getDocumentName() {
        return "document-" + documentCount++;
    }

    Resource makeValidJustification() {
        return makeTextJustification(model, getDocumentName(),
                documentCount * 2, documentCount * 4, system, 1.0);
    }

    /**
     * Add the specified type to the specified resource.
     *
     * @param resource the object to which to add the type
     * @param type     the type of the entity, event, or relation being asserted
     * @return the created type assertion resource
     */
    Resource addType(Resource resource, Resource type) {
        return addType(resource, type, 1.0);
    }

    /**
     * Add the specified type to the specified resource with the specified confidence.
     *
     * @param resource   the object to which to add the type
     * @param type       the type of the entity, event, or relation being asserted
     * @param confidence the confidence with which to mark the specified type
     * @return the created type assertion resource
     */
    Resource addType(Resource resource, Resource type, double confidence) {
        return markType(model, getAssertionUri(), resource, type, system, confidence);
    }

    /**
     * Makes and returns a valid AIF entity object of the specified type.
     */
    Resource makeValidAIFEntity(Resource type) {
        return makeValidAIFEntity(type, null);
    }

    /**
     * Makes and returns a valid entity object of the specified type and URI.
     */
    Resource makeValidAIFEntity(Resource type, String uri) {
        final Resource entity = makeEntity(model, uri == null ? getEntityUri() : uri, system);
        addType(entity, type);
        return entity;
    }

    /**
     * Makes and returns a valid event object of the specified type.
     */
    Resource makeValidAIFEvent(Resource type) {
        return makeValidAIFEvent(type, null);
    }

    /**
     * Makes and returns a valid event object of the specified type and URI.
     */
    Resource makeValidAIFEvent(Resource type, String uri) {
        final Resource event = makeEvent(model, uri == null ? getEventUri() : uri, system);
        addType(event, type);
        return event;
    }

    /**
     * Makes and returns a valid relation object of the specified type.
     */
    Resource makeValidAIFRelation(Resource type) {
        return makeValidAIFRelation(type, null);
    }

    /**
     * Makes and returns a valid relation object of the specified type and URI.
     */
    Resource makeValidAIFRelation(Resource type, String uri) {
        final Resource relation = makeRelation(model, uri == null ? getRelationUri() : uri, system);
        addType(relation, type);
        return relation;
    }

    /**
     * Makes and returns a valid argument assertion between the specified event or relation and an argument filler entity.
     *
     * @param eventOrRelation The event or relation for which to mark the specified argument role
     * @param type            the type of the argument
     * @param argumentFiller  the filler (object) of the argument
     * @return the created event or relation argument assertion
     */
    Resource makeValidAIFEdge(Resource eventOrRelation, Resource type, Resource argumentFiller) {
        return markAsArgument(model, eventOrRelation, type, argumentFiller, system,
                1.0, getAssertionUri());
    }

    /**
     * Makes and returns a valid hypothesis object involving the specified resource(s).
     */
    Resource makeValidAIFHypothesis(Resource... resources) {
        return makeValidAIFHypothesis(null, resources);
    }

    /**
     * Makes and returns a valid hypothesis object involving the specified resource(s) using the specified URI.
     *
     * @param resources A set of entities, relations, and arguments that contribute to the hypothesis
     */
    Resource makeValidAIFHypothesis(String uri, Resource... resources) {
        Set<Resource> set = new HashSet<>();
        Collections.addAll(set, resources);
        return makeHypothesis(model, uri == null ? getHypothesisUri() : uri, set, system);
    }

    /**
     * Assert that the test with the specified description is invalid based on the current model and validator.
     */
    void testInvalid(String testDescription) {
        Resource report = assertAndDump(testDescription, false);
        Map<Triple<Resource, Resource, Resource>, Integer> realCounts = new HashMap<>();
        report.listProperties(SH.result)
                .mapWith(Statement::getObject)
                .mapWith(RDFNode::asResource)
                .mapWith(TestUtils::getTriple)
                .forEachRemaining(triple -> {
                    assertTrue(expectedCounts.containsKey(triple), "Encountered unexpected violation: " + triple);
                    realCounts.put(triple, realCounts.getOrDefault(triple, 0) + 1);
                });

        expectedCounts.forEach((type, count) -> {
            Integer realCount = realCounts.get(type);
            assertNotNull(realCount, "Unable to find violation: " + type);
            assertEquals(count, realCount, "Number of violations don't match: " + type);
        });
    }
    private static Resource getResource(Resource toTest) {
        return toTest != null && toTest.isAnon() ? null : toTest;
    }
    private static Triple<Resource, Resource, Resource> getTriple(Resource result) {
        return new ImmutableTriple<>(
                getResource(result.getPropertyResourceValue(SH.sourceShape)),
                getResource(result.getPropertyResourceValue(SH.sourceConstraintComponent)),
                getResource(result.getPropertyResourceValue(SH.sourceConstraint)));
    }

    /**
     * Specify that a {@code count} number of violations will occur with the specified {@code shape},
     * {@code constraintComponent}, and {@code constraint}.
     *
     * @param shape               {@link Resource} representing the SHACL shape
     * @param constraintComponent {@link Resource} representing the SHACL constraint component
     * @param constraint          {@link Resource} representing the SHACL constraint
     * @param count               number of violations of this type to expect
     */
    void expect(@Nullable Resource shape, @Nullable Resource constraintComponent, @Nullable Resource constraint, int count) {
        expectedCounts.put(new  ImmutableTriple<>(shape, constraintComponent, constraint), count);
    }

    /**
     * Specify that a single violation will occur with the specified {@code shape},
     * {@code constraintComponent}, and {@code constraint}.
     *
     * @param shape               {@link Resource} representing the SHACL shape
     * @param constraintComponent {@link Resource} representing the SHACL constraint component
     * @param constraint          {@link Resource} representing the SHACL constraint
     */
    void expect(@Nullable Resource shape, @Nullable Resource constraintComponent, @Nullable Resource constraint) {
        expect(shape, constraintComponent, constraint, 1);
    }

    /**
     * Assert that the test with the specified description is valid based on the current model and validator.
     */
    void testValid(String testDescription) {
        assertAndDump(testDescription, true);
    }

    /**
     * Assert that the test with the specified description is valid based on the current model, the supplied hypothesis,
     * and the current validator.
     */


    // void testValidWithHypothesis(String testDescription, @Nullable Model hypothesisModel) {
    //     assertAndDumpWithHypothesis(testDescription, true, hypothesisModel);
    // }

    /**
     * Return calling method name from test class.
     * Looks at the calling stack for the calling test.
     * Converts class/method from something like:
     * <code>com.ncc.aif.ExamplesAndValidationTest$ValidExamples</code> / <code>createHierarchicalCluster</code>
     * <code>com.ncc.aif.NistTA3ExamplesAndValidationTest$NISTHypothesisExamples$HypothesisRequiredOneEventOrRelationWithOneEdge</code> / <code>invalidRelationAndEventEdge</code>
     * to:
     * ExamplesAndValidationTest_ValidExamples_createHierarchicalCluster
     * NistTA3ExamplesAndValidationTest_NISTHypothesisExamples_HypothesisRequiredOneEventOrRelationWithOneEdge_invalidRelationAndEventEdge
     */
    private String getCallingMethodName() {
        int steIndex = 0;
        StackTraceElement ste = Thread.currentThread().getStackTrace()[steIndex];

        // Traverse stack from the bottom until we get to methods in this class
        while (!ste.getClassName().contains("TestUtils")) {
            steIndex++;
            ste = Thread.currentThread().getStackTrace()[steIndex];
        }

        // Traverse stack until we get to the first method external to this class that calls a method in this class
        // This is done because this has been refactored several times and I don't want to assume which methods from this class call it
        while (ste.getClassName().contains("TestUtils")) {
            steIndex++;
            ste = Thread.currentThread().getStackTrace()[steIndex];
        }

        String[] pathList = ste.getClassName().split("\\.");
        String className = "";
        if (pathList.length > 0) {
            // We don't want any of the package names in the final string, so just take the part of the string after the last "."
            // Inner classes are concatenated by "$", but it's better to use "_" rather than "$" in filenames.
            String[] nestedClassList = pathList[pathList.length - 1].split("\\$");
            for (int classIndex = 0; classIndex < nestedClassList.length; classIndex++) {
                className += nestedClassList[classIndex];
                if (classIndex < nestedClassList.length - 1) {
                    className += "_";
                }
            }
        }
        return className + "_" + ste.getMethodName();
    }

    /**
     * Return path to file in dump directory. First check if directory exists, and create it if doesn't
     */
    private Path createDirectoryForPath(String filename) throws IOException {
        Path directory = Paths.get("target", DUMP_DIRECTORY);
        Files.createDirectories(directory);
        return Paths.get("target", DUMP_DIRECTORY, filename);
    }

    /**
     * This method writes the specified model to a file ({@link #getCallingMethodName()}
     * if {@link #dumpToFile} is true, o/w writes to System.out
     *
     * @param model {@link Model} of RDF to output
     * @param header {@link String} containing header information for model
     */
    private void dumpModelWithHeader(Model model, String header) {
        if (dumpToFile) {
            String outputFilename = getCallingMethodName() + ".ttl";
            try {
                Path path = createDirectoryForPath(outputFilename);
                logger.info("Dump to " + path);
                writer.write(Files.newOutputStream(path), model);
            } catch (IOException ioe) {
                logger.error("---> Could not dump model to " + outputFilename);
            }
        } else {
            System.out.println("\n" + header);
            writer.write(System.out, model);
        }
    }

    /**
     * This method dumps the model either to stdout or to a file
     *
     * @param testDescription {@link String} containing the description of the test
     */
    private void dumpModel(String testDescription) {
        dumpModelWithHeader(model, "----------------------------------------------\n" + testDescription + "\n\nAIF Model:");
    }

    /**
     * This method dumps the hypothesis and model either to stdout or to a file
     *
     * @param testDescription {@link String} containing the description of the test
     * @param hypothesis {@link Model} containing the hypothesis
     */
    private void dumpHypothesisAndModel(String testDescription, Model hypothesis) {
        dumpModelWithHeader(hypothesis, "----------------------------------------------\n" + testDescription + "\n\nHypothesis:");
        dumpModelWithHeader(model, "AIF Model:");
    }

    /**
     * This method dumps the validation report model either to stdout or to a file
     *
     * @param report  validation report
     */
    private void dumpReport(Resource report) {
        dumpModelWithHeader(report.getModel(), "Failure:");
    }

    /**
     * This method will validate the model using the provided validator and will dump the model as TURTLE if
     * either the validation result is unexpected or if the model is valid and forceDump is true. Thus, forceDump
     * can be used to write all the valid examples to console.
     *
     * @param testDescription {@link String} containing the description of the test
     * @param expected        true if validation is expected to pass, false o/w
     */
    private Resource assertAndDump(String testDescription, boolean expected) {
        return assertAndDumpWithHypothesis(testDescription, expected, null);
    }

    /**
     * This method will validate the model in conjunction with the provided {@code hypothesisModel} using the
     * provided validator and will dump the model as TURTLE if either the validation result is unexpected or
     * if the model is valid and forceDump is true. Thus, forceDump can be used to write all the valid examples to
     * console or file.
     * @param testDescription {@link String} containing the description of the test
     * @param expected        true if validation is expected to pass, false o/w
     * @param hypothesisModel {@link Model} containing a hypothesis in AIF. If null, only model is validated+dumped
     */
    private Resource assertAndDumpWithHypothesis(String testDescription, boolean expected, @Nullable Model hypothesisModel) {
        // test model if no hypothesis is specified, o/w combine and test
        boolean hasHypothesis = hypothesisModel != null;
        Model toTest;
        if (hasHypothesis) {
            toTest = ModelFactory.createDefaultModel();
            toTest.add(model).add(hypothesisModel);
        } else {
            toTest = model;
        }

        final Resource report = validator.validateKBAndReturnReport(toTest);
        final boolean valid = ValidateAIF.isValidReport(report);
        final boolean unexpected = valid != expected;

        // dump model if result is unexpected or if forced
        if (dumpAlways || unexpected) {
            if (hasHypothesis) {
                dumpHypothesisAndModel(testDescription, hypothesisModel);
            } else {
                dumpModel(testDescription);
            }

            // dump report if should dump AND report is invalid
            if (!valid) {
                dumpReport(report);
            }
        }

        // fail if result is unexpected
        if (unexpected) {
            fail("Validation was expected to " + (expected ? "pass" : "fail") + " but did not");
        }

        return report;
    }
}
