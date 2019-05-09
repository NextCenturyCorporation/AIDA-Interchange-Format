package com.ncc.aif;

import ch.qos.logback.classic.Logger;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.ncc.aif.AIFUtils.*;
import static org.junit.jupiter.api.Assertions.fail;

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
        assertAndDump(testDescription, false);
    }

    /**
     * Assert that the test with the specified description is valid based on the current model and validator.
     */
    void testValid(String testDescription) {
        assertAndDump(testDescription, true);
    }

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
     * This method dumps the model either to stdout or to a file
     *
     * @param testDescription {@link String} containing the description of the test
     */
    private void dumpModel(String testDescription) {
        if (dumpToFile) {
            String outputFilename = getCallingMethodName() + ".ttl";

            try {
                Path path = createDirectoryForPath(outputFilename);
                logger.info("Dump to " + path);
                RDFDataMgr.write(java.nio.file.Files.newOutputStream(path), model, RDFFormat.TURTLE_PRETTY);
            } catch (IOException ioe) {
                logger.error("---> Could not dump model to " + outputFilename);
            }
        } else {
            System.out.println("\n----------------------------------------------\n" + testDescription + "\n\nAIF Model:");
            RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
        }
    }

    /**
     * This method dumps the validation report model either to stdout or to a file
     *
     * @param report  validation report
     */
    private void dumpReport(Resource report) {
        if (dumpToFile) {
            String outputReportFilename = getCallingMethodName() + "-report.txt";
            try {
                Path path = createDirectoryForPath(outputReportFilename);
                logger.info("Dump to " + path);
                RDFDataMgr.write(java.nio.file.Files.newOutputStream(path), report.getModel(), RDFFormat.TURTLE_PRETTY);
            } catch (IOException ioe) {
                logger.error("---> Could not dump report to " + outputReportFilename);
            }
        } else {
            System.out.println("\nFailure:");
            RDFDataMgr.write(System.out, report.getModel(), RDFFormat.TURTLE_PRETTY);
        }
    }

    /**
     * This method will validate the model using the provided validator and will dump the model as TURTLE if
     * either the validation result is unexpected or if the model is valid and forceDump is true. Thus, forceDump
     * can be used to write all the valid examples to console.
     *
     * @param testDescription {@link String} containing the description of the test
     * @param expected        true if validation is expected to pass, false o/w
     */
    private void assertAndDump(String testDescription, boolean expected) {
        final Resource report = validator.validateKBAndReturnReport(model);
        final boolean valid = ValidateAIF.isValidReport(report);

        // dump model if result is unexpected or if forced
        if (dumpAlways || valid != expected) {
            dumpModel(testDescription);
        }


        // dump report if forced to and model isn't valid OR result is unexpected
        if (!valid) {   // There is a report that could be dumped.

            // Dump the report if forced to, or if the result is unexpected
            if (dumpAlways || valid != expected) {
                dumpReport(report);
            }
        }

        // fail if result is unexpected
        if (valid != expected) {
            fail("Validation was expected to " + (expected ? "pass" : "fail") + " but did not");
        }

    }
}
